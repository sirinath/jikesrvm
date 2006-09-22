/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */
package org.mmtk.policy;

import org.mmtk.plan.Plan;
import org.mmtk.plan.refcount.RCBase;
import org.mmtk.plan.refcount.RCBaseMutator;
import org.mmtk.utility.alloc.BlockAllocator;
import org.mmtk.utility.alloc.SegregatedFreeList;
import org.mmtk.utility.deque.*;
import org.mmtk.utility.Log;
import org.mmtk.utility.options.Options;
import org.mmtk.utility.scan.Scan;
import org.mmtk.utility.statistics.*;
import org.mmtk.utility.TrialDeletion;
import org.mmtk.utility.Constants;

import org.mmtk.vm.VM;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * This class implements thread-local behavior for a reference counted
 * space.  Each instance of this class captures state associated with
 * one thread/CPU acting over a particular reference counted space.
 * Since all state is thread local, instance methods of this class are
 * not required to be synchronized.
 * 
 * @author Steve Blackburn
 * @version $Revision$
 * @date $Date$
 */
public final class RefCountLocal extends SegregatedFreeList
  implements Constants, Uninterruptible {
  public final static String Id = "$Id$"; 

  /****************************************************************************
   * 
   * Class variables
   */
  public static final int META_DATA_PAGES_PER_REGION = SegregatedFreeList.META_DATA_PAGES_PER_REGION_WITH_BITMAP; 

  private static SharedDeque oldRootPool;

  // sanity tracing
  private static SharedDeque incSanityRootsPool;
  private static SharedDeque sanityWorkQueuePool;
  private static SharedDeque checkSanityRootsPool;
  private static SharedDeque sanityImmortalPoolA;
  private static SharedDeque sanityImmortalPoolB;
  private static SharedDeque sanityLastGCPool;
  public static int rcLiveObjects = 0;
  public static int sanityLiveObjects = 0;

  private static final int DEC_COUNT_QUANTA = 2000; // do 2000 decs at a time
  private static final double DEC_TIME_FRACTION = 0.66; // 2/3 remaining time

  // Statistics
  private static Timer decTime;
  private static Timer incTime;
  private static Timer cdTime;


  /****************************************************************************
   * 
   * Instance variables
   */
  private RefCountSpace rcSpace;
  private LargeRCObjectLocal los;

  private ObjectReferenceDeque decBuffer;
  private ObjectReferenceDeque newRootSet;
  private ObjectReferenceDeque oldRootSet;

  private boolean decrementPhase = false;

  private TrialDeletion cycleDetector;

  // counters
  private int incCounter;
  private int decCounter;
  private int rootCounter;
  private int purpleCounter;

  // sanity tracing
  private ObjectReferenceDeque incSanityRoots;
  private AddressPairDeque sanityWorkQueue;
  private ObjectReferenceDeque checkSanityRoots;
  private ObjectReferenceDeque sanityImmortalSetA;
  private ObjectReferenceDeque sanityImmortalSetB;
  private ObjectReferenceDeque sanityLastGCSet;

  protected final boolean preserveFreeList() { return true; }
  protected final boolean maintainInUse() { return true; }
  protected final boolean maintainSideBitmap() { return true; }

  /****************************************************************************
   * 
   * Initialization
   */

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {
    if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(LAZY_SWEEP);
    oldRootPool = new SharedDeque(Plan.metaDataSpace, 1);
    oldRootPool.newConsumer();

    if (RefCountSpace.RC_SANITY_CHECK) {
      incSanityRootsPool = new SharedDeque(Plan.metaDataSpace, 1);
      incSanityRootsPool.newConsumer();
      sanityWorkQueuePool = new SharedDeque(Plan.metaDataSpace, 2);
      sanityWorkQueuePool.newConsumer();
      checkSanityRootsPool = new SharedDeque(Plan.metaDataSpace, 1);
      checkSanityRootsPool.newConsumer();
      sanityImmortalPoolA = new SharedDeque(Plan.metaDataSpace, 1);
      sanityImmortalPoolA.newConsumer();
      sanityImmortalPoolB = new SharedDeque(Plan.metaDataSpace, 1);
      sanityImmortalPoolB.newConsumer();
      sanityLastGCPool = new SharedDeque(Plan.metaDataSpace, 1);
      sanityLastGCPool.newConsumer();
    }

    cellSize = new int[SIZE_CLASSES];
    blockSizeClass = new byte[SIZE_CLASSES];
    cellsInBlock = new int[SIZE_CLASSES];
    blockHeaderSize = new int[SIZE_CLASSES];

    for (int sc = 0; sc < SIZE_CLASSES; sc++) {
      cellSize[sc] = getBaseCellSize(sc);
      for (byte blk = 0; blk < BlockAllocator.BLOCK_SIZE_CLASSES; blk++) {
        int usableBytes = BlockAllocator.blockSize(blk);
        int cells = usableBytes / cellSize[sc];
        blockSizeClass[sc] = blk;
        cellsInBlock[sc] = cells;
        /* cells must start at multiple of MIN_ALIGNMENT because
           cellSize is also supposed to be multiple, this should do
           the trick: */
        blockHeaderSize[sc] = BlockAllocator.blockSize(blk) - cells * cellSize[sc];
        if (((usableBytes < BYTES_IN_PAGE) && (cells*2 > MAX_CELLS)) ||
            ((usableBytes > (BYTES_IN_PAGE>>1)) && (cells > MIN_CELLS)))
          break;
      }
    }
    decTime = new Timer("dec", false, true);
    incTime = new Timer("inc", false, true);
    cdTime = new Timer("cd", false, true);
  }

  /**
   * Constructor
   * 
   * @param space The ref count space with which this local thread is
   * associated.
   * @param los The large object allocator
   * @param dec The decBuffer
   * @param root The rootSet buffer.
   */
  public RefCountLocal(RefCountSpace space, LargeRCObjectLocal los,
      ObjectReferenceDeque dec, ObjectReferenceDeque root) {
    super(space);
    rcSpace = space;
    this.los = los;

    decBuffer = dec;
    newRootSet = root;
    oldRootSet = new ObjectReferenceDeque("old root set", oldRootPool);
    if (RefCountSpace.RC_SANITY_CHECK) {
      incSanityRoots = new ObjectReferenceDeque("sanity increment root set", incSanityRootsPool);
      sanityWorkQueue = new AddressPairDeque(sanityWorkQueuePool);
      checkSanityRoots = new ObjectReferenceDeque("sanity check root set", checkSanityRootsPool);
      sanityImmortalSetA = new ObjectReferenceDeque("immortal set A", sanityImmortalPoolA);
      sanityImmortalSetB = new ObjectReferenceDeque("immortal set B", sanityImmortalPoolB);
      sanityLastGCSet = new ObjectReferenceDeque("last GC set", sanityLastGCPool);
    }
    if (RCBase.REF_COUNT_CYCLE_DETECTION)
      cycleDetector = new TrialDeletion(this);
  }

  /****************************************************************************
   * 
   * Allocation
   */

  /**
   * Prepare the next block in the free block list for use by the free
   * list allocator.  In the case of lazy sweeping this involves
   * sweeping the available cells.  <b>The sweeping operation must
   * ensure that cells are pre-zeroed</b>, as this method must return
   * pre-zeroed cells.
   *
   * @param block The block to be prepared for use
   * @param sizeClass The size class of the block
   * @return The address of the first pre-zeroed cell in the free list
   * for this block, or zero if there are no available cells.
   */
  protected final Address advanceToBlock(Address block, int sizeClass) {
    return makeFreeListFromLiveBits(block, sizeClass);
  }

  /****************************************************************************
   * 
   * Collection
   */

  /**
   * Prepare for a collection.
   */
  public final void prepare(boolean time) {
    if (RefCountSpace.RC_SANITY_CHECK && !Options.noFinalizer.getValue())
      VM.assertions.fail("Ref count sanity checks must be run with finalization disabled (-X:gc:noFinalizer=true)");

    flushFreeLists();
    if (RefCountSpace.INC_DEC_ROOT) {
      if (Options.verbose.getValue() > 2)
        processRootBufsAndCount();
      else
        processRootBufs();
    }
  }

  public final void release() {
    VM.assertions._assert(false);
  }

  /**
   * Finish up after a collection.
   * 
   * @param plan The plan instance performing this operation
   * @param primary Is this the thread to use for single-threaded work?
   */
  public final void release(RCBaseMutator plan, boolean primary) {
    boolean timekeeper = (Options.verboseTiming.getValue() && primary);
    // RC_PREPARE_MUTATOR
    flushFreeLists();
    VM.collection.rendezvous(4400);
    // RC_PROCESS_OLD_ROOTS
    if (!RefCountSpace.INC_DEC_ROOT) {
      processOldRootBufs(plan);
    }
    // RC_SANITY_INCREMENT
    if (timekeeper) decTime.start();
    if (RefCountSpace.RC_SANITY_CHECK) incSanityTrace();
    // RC_PROCESS_DEC_BUFS
    processDecBufs(plan);
    if (timekeeper) decTime.stop();
    VM.collection.rendezvous(4410);
    // RC_SWEEP
    sweepBlocks();
    // RC_CYCLE_DETECTION
    if (RCBase.REF_COUNT_CYCLE_DETECTION) {
      if (timekeeper) cdTime.start();
      if (cycleDetector.collectCycles(primary, timekeeper))
        processDecBufs(plan);
      if (timekeeper) cdTime.stop();
    }
    VM.collection.rendezvous(4420);
    // RC_SANITY_TRACE
    if (RefCountSpace.RC_SANITY_CHECK) checkSanityTrace();
    // RC_PROCESS_ROOT_BUFS
    if (!RefCountSpace.INC_DEC_ROOT) {
      if (Options.verbose.getValue() > 2)
        processRootBufsAndCount();
      else
        processRootBufs();
    }
    // RELEASE_MUTATOR
    restoreFreeLists();
  }

  /**
   * Process the decrement buffers
   */
  private final void processDecBufs(RCBaseMutator plan) {
    ObjectReference tgt = ObjectReference.nullReference();
    long tc = Plan.getTimeCap();
    long remaining = tc - VM.statistics.cycles();
    long limit = tc - (long) (remaining * (1 - DEC_TIME_FRACTION));
    decrementPhase = true;
    decCounter = 0;
    do {
      int count = 0;
      while (count < DEC_COUNT_QUANTA && !(tgt = decBuffer.pop()).isNull()) {
        decrement(tgt, plan);
        count++;
      }
      decCounter += count;
    } while (!tgt.isNull() && (RefCountSpace.RC_SANITY_CHECK || VM.statistics.cycles() < limit));
    decrementPhase = false;
  }

  /**
   * Process the root buffers from the previous GC, if the object is
   * no longer live release it.
   * 
   * @param plan The plan instance performing this operation
   */
  private final void processOldRootBufs(RCBaseMutator plan) {
    ObjectReference object;
    while (!(object = oldRootSet.pop()).isNull()) {
      if (!RefCountSpace.isLiveRC(object))
        release(object, plan);
    }
  }

  /**
   * Process the root buffers, moving entries over to the decrement
   * buffers for the next GC. 
   */
  private final void processRootBufs() {
    ObjectReference object;
    while (!(object = newRootSet.pop()).isNull()) {
      if (RefCountSpace.INC_DEC_ROOT)
        decBuffer.push(object);
      else {
        RefCountSpace.unsetRoot(object);
        oldRootSet.push(object);
      }
    }
  }

  /**
   * Process the root buffers and maintain statistics.
   */
  private final void processRootBufsAndCount() {
    ObjectReference object;
    rootCounter = 0;
    while (!(object = newRootSet.pop()).isNull()) {
      if (RefCountSpace.INC_DEC_ROOT)
        decBuffer.push(object);
      else {
        RefCountSpace.unsetRoot(object);
        oldRootSet.push(object);
      }
      rootCounter++;
    }
  }

  /****************************************************************************
   * 
   * Object processing and tracing
   */

  /**
   * Decrement the reference count of an object.  If the count drops
   * to zero, the release the object, performing recursive decremetns
   * and freeing the object.  If not, then if cycle detection is being
   * used, record this object as the possible source of a cycle of
   * garbage (all non-zero decrements are potential sources of new
   * cycles of garbage.
   *
   * @param object The object whose count is to be decremented
   * @param plan The plan instance performing this operation
   */
  public final void decrement(ObjectReference object, RCBaseMutator plan)
      throws InlinePragma {
    int state = RefCountSpace.decRC(object);
    if (state == RefCountSpace.DEC_KILL)
      release(object, plan);
    else if (RCBase.REF_COUNT_CYCLE_DETECTION && 
             state == RefCountSpace.DEC_BUFFER)
      cycleDetector.possibleCycleRoot(object);
  }

  /**
   * An object is dead, so before freeing it, scan the object for
   * recursive decrement (each outgoing pointer from this dead object
   * is now dead, so the targets must have their counts decremented).<p>
   *
   * If the object is being held in a buffer by the cycle detector,
   * then the object must not be freed.  It will be freed later when
   * the cycle detector processes its buffers.
   *
   * @param object The object to be released
   * @param plan The plan instance performing this operation
   */
  private final void release(ObjectReference object, RCBaseMutator plan)
      throws InlinePragma {
    // this object is now dead, scan it for recursive decrement
    if (RefCountSpace.RC_SANITY_CHECK) rcLiveObjects--;
    Scan.enumeratePointers(object, plan.decEnum);
    if (!RCBase.REF_COUNT_CYCLE_DETECTION ||
        !RefCountSpace.isBuffered(object)) 
      free(object);
  }

  /**
   * Free an object.  First determine whether it is managed by the LOS
   * or the regular free list.  If managed by LOS, delegate freeing to
   * the LOS.  Otherwise, establish the cell, block and sizeclass for
   * this object and call the free method of our subclass.
   * 
   * @param object The object to be freed.
   */
  public final void free(ObjectReference object) 
    throws InlinePragma {
    if (Space.isInSpace(Plan.LOS, object))
      los.free(VM.objectModel.refToAddress(object));
    else
      deadObject(object);
  }

  /****************************************************************************
   * 
   * Sanity check support
   * 
   * Sanity check code allows reference counts to be cross-checked
   * with counts established via a transitive closure.  The code has
   * two significant limitations:
   *
   * . Finalization is not supported---it must be turned off to avoid
   *   anomalies relating to finalization's odd reachability semantics.
   *
   * . Currently immortal (and boot image) objects are uncollected.
   *   If any such object were to become unreachable, decrements would
   *   not be issued for any referent RC objects---leading to a
   *   discrepancy between RC and sanity RC counts.
   *
   * To maximize the utility of this mechanism in the face of the
   * above problems, it is best to trigger frequent GCs by setting the
   * metadata limit to its minimum.
   */

  /**
   * Add an entry to the root buffer for the increment sanity
   * traversal. (used only for sanity checks).
   * 
   * @param object The object to be added to the root buffer
   */
  public final void incSanityTraceRoot(ObjectReference object) {
    incSanityRoots.push(object);
  }

  /**
   * Add an entry to the sanity traversal work queue (the work queue
   * is used instead of a stack in establishing the transitive
   * closure). (used only for sanity checks).
   *
   * @param object The object to be added to the work queue buffer
   * @param location The location from which the object is reached
   */
  public final void sanityTraceEnqueue(ObjectReference object, 
                                       Address location) {
    sanityWorkQueue.push(object.toAddress(), location);
  }


  /**
   * Perform a sanity increment traversal.  This involves starting
   * from roots and performing a transitive closure, incrementing the
   * sanity reference count of each object each time it is visited.
   */
  final void incSanityTrace() {
    sanityLiveObjects = 0;
    ObjectReference object;
    while (!(object = sanityImmortalSetA.pop()).isNull()) {
      RCBase.collector().checkSanityTrace(object, Address.zero());
      sanityImmortalSetB.push(object);
    }
    while (!(object = incSanityRoots.pop()).isNull()) {
      RCBase.collector().incSanityTrace(object, Address.zero(), true);
      checkSanityRoots.push(object);
    }
    while (!(object = sanityWorkQueue.pop1().toObjectReference()).isNull()) {
      RCBase.collector().incSanityTrace(object, sanityWorkQueue.pop2(), false);
    }
  }

  /**
   * Perform a sanity check traversal.  This involves starting from
   * roots and performing a transitive closure, checking the sanity
   * reference count of each object against its actual reference
   * count, failing with an error if there is a mismatch.  A check is
   * also made of the number of live objects (comparing RC and
   * tracing).
   */
  final void checkSanityTrace() {
    ObjectReference object;
    while (!(object = sanityLastGCSet.pop()).isNull()) {
      RefCountSpace.checkOldObject(object);
    }
    while (!(object = sanityImmortalSetB.pop()).isNull()) {
      RCBase.collector().checkSanityTrace(object, Address.zero());
      sanityImmortalSetA.push(object);
    }
    while (!(object = checkSanityRoots.pop()).isNull()) {
      if (VM.statistics.getCollectionCount() == 1) checkForImmortal(object);
      RCBase.collector().checkSanityTrace(object, Address.zero());
    }
    while (!(object = sanityWorkQueue.pop1().toObjectReference()).isNull()) {
      if (VM.statistics.getCollectionCount() == 1) checkForImmortal(object);
      RCBase.collector().checkSanityTrace(object, sanityWorkQueue.pop2());
    }
    if (rcLiveObjects != sanityLiveObjects) {
      Log.write("live mismatch: "); Log.write(rcLiveObjects); 
      Log.write(" (rc) != "); Log.write(sanityLiveObjects);
      Log.writeln(" (sanityRC)");
      if (VM.VERIFY_ASSERTIONS) VM.assertions._assert(false);
    }
  }

  static int lastGCsize = 0;
  public final void addLiveSanityObject(ObjectReference object) {
    lastGCsize++;
    sanityLastGCSet.push(object);
  }

  public final void addImmortalObject(ObjectReference object) {
    sanityImmortalSetA.push(object);
  }

  final void checkForImmortal(ObjectReference object) {
    if (Space.getSpaceForObject(object) instanceof ImmortalSpace)
      addImmortalObject(object);
  }

  /**
   * An allocation has occured, so increment the count of live objects.
   */
  public final static void sanityAllocCount(ObjectReference object) {
    rcLiveObjects++;
  }

 
  /****************************************************************************
   * 
   * Misc
   */

  /**
   * Setter method for the purple counter.
   * 
   * @param purple The new value for the purple counter.
   */
  public final void setPurpleCounter(int purple) {
    purpleCounter = purple;
  }

  /**
   * Print out statistics on increments, decrements, roots and
   * potential garbage cycles (purple objects).
   */
  public final void printStats() {
    Log.write("<GC "); Log.write(Stats.gcCount()); Log.write(" "); 
    Log.write(incCounter); Log.write(" incs, ");
    Log.write(decCounter); Log.write(" decs, ");
    Log.write(rootCounter); Log.write(" roots");
    if (RCBase.REF_COUNT_CYCLE_DETECTION) {
      Log.write(", ");
      Log.write(purpleCounter);Log.write(" purple");
    }
    Log.writeln(">");
  }
}
