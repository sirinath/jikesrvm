/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.*;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.VM_Processor;

/**
 * This class implements a simple reference counting collector.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public final class Plan extends BasePlan implements VM_Uninterruptible { // implements Constants 
  public final static String Id = "$Id$"; 

  public static final boolean needsWriteBarrier = true;
  public static final boolean needsRefCountWriteBarrier = true;
  public static final boolean refCountCycleDetection = false;
  public static final boolean movesObjects = false;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public static methods (aka "class methods")
  //
  // Static methods and fields of Plan are those with global scope,
  // such as virtual memory and memory resources.  This stands in
  // contrast to instance methods which are for fast, unsychronized
  // access to thread-local structures such as bump pointers and
  // remsets.
  //

  public static void boot()
    throws VM_PragmaInterruptible {
    BasePlan.boot();
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  public static VM_Address traceObject(VM_Address obj) {
    return traceObject(obj, false);
  }
  public static VM_Address traceObject(VM_Address obj, boolean root) {
    VM_Address addr = VM_Interface.refToAddress(obj);
    if (addr.LE(HEAP_END) && addr.GE(RC_START))
      return rcCollector.traceObject(obj, root);
    
    // else this is not a rc heap pointer
    return obj;
  }

  public static boolean isLive(VM_Address obj) {
    VM_Address addr = VM_ObjectModel.getPointerInMemoryRegion(obj);
    if (addr.LE(HEAP_END)) {
      if (addr.GE(RC_START))
 	return rcCollector.isLive(obj);
      else if (addr.GE(IMMORTAL_START))
 	return true;
    } 
    return false;
  }
  
  public static void showPlans() {
    for (int i=0; i<VM_Scheduler.processors.length; i++) {
      VM_Processor p = VM_Scheduler.processors[i];
      if (p == null) continue;
      VM.sysWrite(i, ": ");
      p.mmPlan.show();
    }
  }

  public static void showUsage() {
      VM.sysWrite("used pages = ", getPagesUsed());
      VM.sysWrite(" ("); VM.sysWrite(Conversions.pagesToBytes(getPagesUsed()) >> 20, " Mb) ");
      VM.sysWrite("= (rc) ", rcMR.reservedPages());
      VM.sysWrite(" + (imm) ", immortalMR.reservedPages());
      VM.sysWriteln(" + (md) ", metaDataMR.reservedPages());
 }

//   public static int getInitialHeaderValue(int size) {
//     return rcCollector.getInitialHeaderValue(size);
//   }

  public static int resetGCBitsForCopy(VM_Address fromObj, int forwardingPtr,
				       int bytes) {
    if (VM.VerifyAssertions)
      VM._assert(false);  // this is not a copying collector!
    return forwardingPtr;
  }

  public static final long freeMemory() throws VM_PragmaUninterruptible {
    return totalMemory() - usedMemory();
  }

  public static final long usedMemory() throws VM_PragmaUninterruptible {
    return Conversions.pagesToBytes(getPagesUsed());
  }

  public static final long totalMemory() throws VM_PragmaUninterruptible {
    return Conversions.pagesToBytes(getPagesAvail());
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Public instance methods
  //
  // Instances of Plan map 1:1 to "kernel threads" (aka CPUs or in
  // Jikes RVM, VM_Processors).  Thus instance methods allow fast,
  // unsychronized access to Plan utilities such as allocation and
  // collection.  Each instance rests on static resources (such as
  // memory and virtual memory resources) which are "global" and
  // therefore "static" members of Plan.
  //

  /**
   * Constructor
   */
  public Plan() {
    rc = new SimpleRCAllocator(rcCollector);
    immortal = new BumpPointer(immortalVM);
    incBuffer = new AddressQueue("inc buf", incPool);
    decBuffer = new AddressQueue("dec buf", decPool);
    rootSet = new AddressQueue("root set", rootPool);
    if (refCountCycleDetection) {
      cycleBufferA = new AddressQueue("cycle buf A", cyclePoolA);
      cycleBufferB = new AddressQueue("cycle buf B", cyclePoolB);
      freeBuffer = new AddressQueue("free buffer", freePool);
    }
  }

  /**
   * Allocate space (for an object)
   *
   * @param allocator The allocator number to be used for this allocation
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @param advice Statically-generated allocation advice for this allocation
   * @return The address of the first byte of the allocated region
   */
  public final VM_Address alloc(EXTENT bytes, boolean isScalar, int allocator, 
				AllocAdvice advice)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(bytes == (bytes & (~(WORD_SIZE-1))));
    VM_Address region;
    switch (allocator) {
      case       RC_ALLOCATOR: region = rc.alloc(isScalar, bytes); break;
      case IMMORTAL_ALLOCATOR: region = immortal.alloc(isScalar, bytes); break;
      default:                 region = VM_Address.zero(); VM.sysFail("No such allocator");
    }
    if (VM.VerifyAssertions) VM._assert(Memory.assertIsZeroed(region, bytes));
    return region;
  }
  
  public final void postAlloc(Object ref, Object[] tib, int size,
			      boolean isScalar, int allocator)
    throws VM_PragmaInline {
    if (allocator == RC_ALLOCATOR) {
      decBuffer.push(VM_Magic.objectAsAddress(ref));
    }
  }

  public final void postCopy(Object ref, Object[] tib, int size,
			     boolean isScalar) {
    if (VM.VerifyAssertions)
      VM._assert(false);
  }

  public final void show() {
    rc.show();
  }

  /**
   * Allocate space for copying an object (this method <i>does not</i>
   * copy the object, it only allocates space)
   *
   * @param original A reference to the original object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @return The address of the first byte of the allocated region
   */
  public final VM_Address allocCopy(VM_Address original, EXTENT bytes,
				    boolean isScalar) throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(false);
    return null;
  }

  /**
   * Advise the compiler/runtime which allocator to use for a
   * particular allocation.  This should be called at compile time and
   * the returned value then used for the given site at runtime.
   *
   * @param type The type id of the type being allocated
   * @param bytes The size (in bytes) required for this object
   * @param callsite Information identifying the point in the code
   * where this allocation is taking place.
   * @param hint A hint from the compiler as to which allocator this
   * site should use.
   * @return The allocator number to be used for this allocation.
   */
  public final int getAllocator(Type type, EXTENT bytes, CallSite callsite,
				AllocAdvice hint) {
    return RC_ALLOCATOR;
  }

  /**
   * Give the compiler/runtime statically generated alloction advice
   * which will be passed to the allocation routine at runtime.
   *
   * @param type The type id of the type being allocated
   * @param bytes The size (in bytes) required for this object
   * @param callsite Information identifying the point in the code
   * where this allocation is taking place.
   * @param hint A hint from the compiler as to which allocator this
   * site should use.
   * @return Allocation advice to be passed to the allocation routine
   * at runtime
   */
  public final AllocAdvice getAllocAdvice(Type type, EXTENT bytes,
					  CallSite callsite,
					  AllocAdvice hint) { 
    return null;
  }

  /**
   * This method is called periodically by the allocation subsystem
   * (by default, each time a page is consumed), and provides the
   * collector with an opportunity to collect.<p>
   *
   * We trigger a collection whenever an allocation request is made
   * that would take the number of pages in use (committed for use)
   * beyond the number of pages available.  Collections are triggered
   * through the runtime, and ultimately call the
   * <code>collect()</code> method of this class or its superclass.
   *
   * This method is clearly interruptible since it can lead to a GC.
   * However, the caller is typically uninterruptible and this fiat allows 
   * the interruptibility check to work.  The caveat is that the caller 
   * of this method must code as though the method is interruptible. 
   * In practice, this means that, after this call, processor-specific
   * values must be reloaded.
   *
   * @return Whether a collection is triggered
   */

  public boolean poll(boolean mustCollect, MemoryResource mr)
    throws VM_PragmaLogicallyUninterruptible {
    if (gcInProgress) return false;
    if (mustCollect || getPagesReserved() > getTotalPages()) {
      if (VM.VerifyAssertions) VM._assert(mr != metaDataMR);
      required = mr.reservedPages() - mr.committedPages();
      VM_Interface.triggerCollection();
      return true;
    }
    return false;
  }
  
  public final SimpleRCCollector getRC() {
    return rcCollector;
  }

  public static int getInitialHeaderValue(int size) {
    return rcCollector.getInitialHeaderValue(size);
  }

  public final boolean hasMoved(VM_Address obj) {
    return true;
  }

  public final SimpleRCAllocator getAllocator() {
    return rc;
  }
  /**
   * Perform a collection.
   */
  public void collect () {
    prepare();
    super.collect();
    release();
  }

  /* We reset the state for a GC thread that is not participating in this GC
   */
  public void prepareNonParticipating() {
    allPrepare(NON_PARTICIPANT);
  }

  public void putFieldWriteBarrier(VM_Address src, int offset, VM_Address tgt)
    throws VM_PragmaInline {
    writeBarrier(src.add(offset), tgt);
  }

  public void arrayStoreWriteBarrier(VM_Address src, int index, VM_Address tgt)
    throws VM_PragmaInline {
    writeBarrier(src.add(index<<LOG_WORD_SIZE), tgt);
  }
  public void arrayCopyWriteBarrier(VM_Address src, int startIndex, 
				    int endIndex)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions)
      VM._assert(false);
  }

  public final void arrayCopyRefCountWriteBarrier(VM_Address src, VM_Address tgt) 
    throws VM_PragmaInline {
    writeBarrier(src, tgt);
  }
  private void writeBarrier(VM_Address src, VM_Address tgt) 
    throws VM_PragmaInline {
    if (GATHER_WRITE_BARRIER_STATS) wbFastPathCounter++;
    VM_Address old = VM_Magic.getMemoryAddress(src);
    if (old.GE(RC_START))
      decBuffer.push(old);
    if (tgt.GE(RC_START))
      incBuffer.push(tgt);
  }

  public final void addToDecBuf(VM_Address obj)
    throws VM_PragmaInline {
    decBuffer.push(obj);
  }
  public final void addToIncBuf(VM_Address obj)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(false);
  }
  public void addToRootSet(VM_Address root) 
    throws VM_PragmaInline {
    rootSet.push(VM_Magic.objectAsAddress(root));
  }
  public final void addToCycleBuf(VM_Address obj)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions && !refCountCycleDetection) VM._assert(false);
    if (cycleBufferAisOpen)
      cycleBufferA.push(obj);
    else
      cycleBufferB.push(obj);
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private class methods
  //

  /**
   * Return the number of pages reserved for use.
   *
   * @return The number of pages reserved given the pending allocation
   */
  private static int getPagesReserved() {

    int pages = rcMR.reservedPages();
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }


  private static int getPagesUsed() {
    int pages = rcMR.reservedPages();
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }

  // Assuming all future allocation comes from semispace
  //
  private static int getPagesAvail() {
    return getTotalPages() - rcMR.reservedPages() - immortalMR.reservedPages() - metaDataMR.reservedPages();
  }

  private static final String allocatorToString(int type) {
    switch (type) {
      case RC_ALLOCATOR: return "Ref count";
      case IMMORTAL_ALLOCATOR: return "Immortal";
      default: return "Unknown";
   }
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Private and protected instance methods
  //

  /**
   * Prepare for a collection.  Called by BasePlan which will make
   * sure only one thread executes this.
   */
  protected void singlePrepare() {
    if (verbose == 1) {
      VM.sysWrite(Conversions.pagesToBytes(getPagesUsed())>>10);
    }
    if (verbose > 2) {
      VM.sysWrite("Collection ", gcCount);
      VM.sysWrite(":      reserved = ", getPagesReserved());
      VM.sysWrite(" (", Conversions.pagesToBytes(getPagesReserved()) / ( 1 << 20)); 
      VM.sysWrite(" Mb) ");
      VM.sysWrite("      trigger = ", getTotalPages());
      VM.sysWrite(" (", Conversions.pagesToBytes(getTotalPages()) / ( 1 << 20)); 
      VM.sysWriteln(" Mb) ");
      VM.sysWrite("  Before Collection: ");
      showUsage();
    }
    Immortal.prepare(immortalVM, null);
    rcCollector.prepare(rcVM, rcMR);
  }

  protected void allPrepare(int id) {
    rc.prepare();
    processRootBufs();  // decrements from previous collection
  }

  protected void allRelease(int id) {
    processIncBufs();
//     VM.sysWrite("processed incs\n");
    if (id == 1)
      rcCollector.decrementPhase();
    VM_CollectorThread.gcBarrier.rendezvous();
    processDecBufs();
    if (refCountCycleDetection) {
      processCycleBufs();
      processFreeBufs();
      if (true) {
	doMarkGreyPhase();
	doScanPhase();
	doCollectPhase();
      }
    }
    if (GATHER_WRITE_BARRIER_STATS) { 
      // This is printed independantly of the verbosity so that any
      // time someone sets the GATHER_WRITE_BARRIER_STATS flags they
      // will know---it will have a noticable performance hit...
      VM.sysWrite("<GC ", gcCount); VM.sysWrite(" "); 
      VM.sysWrite(wbFastPathCounter, false); VM.sysWrite(" wb-fast>\n");
      wbFastPathCounter = 0;
    }
  }

  /**
   * Clean up after a collection.
   */
  protected void singleRelease() {
    // release each of the collected regions
    rcCollector.release(rc);
    Immortal.release(immortalVM, null);
    if (verbose == 1) {
      VM.sysWrite("->");
      VM.sysWrite(Conversions.pagesToBytes(getPagesUsed())>>10);
      VM.sysWrite("KB ");
    }
    if (verbose == 2) {
      VM.sysWrite("<GC ", gcCount); VM.sysWrite(" "); 
      VM.sysWrite(incCounter, false); VM.sysWrite(" incs, ");
      VM.sysWrite(decCounter, false); VM.sysWrite(" decs, ");
      VM.sysWrite(rootCounter, false); VM.sysWrite(" roots");
      if (refCountCycleDetection) {
	VM.sysWrite(", "); 
	VM.sysWrite(purpleCounter, false); VM.sysWriteln(" purple");
      }
      VM.sysWrite(">\n");
    }
    if (verbose > 2) {
      VM.sysWrite("   After Collection: ");
      showUsage();
    }
    if (getPagesReserved() + required >= getTotalPages()) {
      if (!progress)
	VM.sysFail("Out of memory");
      progress = false;
    } else
      progress = true;
  }

  private final void processIncBufs() {
    VM_Address tgt;
    if (verbose == 2) {
      incCounter = 0;
      while (!(tgt = incBuffer.pop()).isZero()) {
	rcCollector.incRC(tgt);
	incCounter++;
      }
    } else
      while (!(tgt = incBuffer.pop()).isZero())
	rcCollector.incRC(tgt);
 }

  private final void processDecBufs() {
    VM_Address tgt;
    if (verbose == 2) {
      decCounter = 0;
      while (!(tgt = decBuffer.pop()).isZero()) {
	rcCollector.decRC(tgt, rc, this);
	decCounter++;
      }
    } else
      while (!(tgt = decBuffer.pop()).isZero())
	rcCollector.decRC(tgt, rc, this);
  }

  // FIXME this is inefficient!
  private final void processRootBufs() {
    VM_Address tgt;
    if (verbose == 2) {
      rootCounter = 0;
      while (!(tgt = rootSet.pop()).isZero()) {
	decBuffer.push(tgt);
	rootCounter++;
      }
    } else
      while (!(tgt = rootSet.pop()).isZero())
	decBuffer.push(tgt);
  }

  public void addToFreeBuf(VM_Address object) 
   throws VM_PragmaInline {
    freeBuffer.push(object);
  }
  private final void processCycleBufs() {
    VM_Address obj;
    AddressQueue src = (cycleBufferAisOpen) ? cycleBufferA : cycleBufferB;
    AddressQueue tgt = (cycleBufferAisOpen) ? cycleBufferB : cycleBufferA;
    purpleCounter = 0;
    while (!(obj = src.pop()).isZero()) {
      purpleCounter++;
      if (VM.VerifyAssertions) VM._assert(!SimpleRCHeader.isGreen(obj));
      if (SimpleRCHeader.isLiveRC(VM_Magic.addressAsObject(obj))) {
	if (SimpleRCHeader.isPurple(VM_Magic.addressAsObject(obj)))
	  tgt.push(obj);
	else
	  SimpleRCHeader.clearBufferedBit(VM_Magic.addressAsObject(obj));
      } else
	freeBuffer.push(obj);
    }
    cycleBufferAisOpen = !cycleBufferAisOpen;
  }
  private final void processFreeBufs() {
    VM_Address obj;
    while (!(obj = freeBuffer.pop()).isZero())
      rcCollector.free(obj, rc);
  }
  private final void doMarkGreyPhase() {
    VM_Address obj;
    AddressQueue src = (cycleBufferAisOpen) ? cycleBufferA : cycleBufferB;
    AddressQueue tgt = (cycleBufferAisOpen) ? cycleBufferB : cycleBufferA;
    rcCollector.markGreyPhase();
    while (!(obj = src.pop()).isZero()) {
      if (VM.VerifyAssertions) VM._assert(!SimpleRCHeader.isGreen(obj));
      if (SimpleRCHeader.isPurple(obj) && SimpleRCHeader.isLiveRC(obj)) {
	if (VM.VerifyAssertions) VM._assert(!SimpleRCHeader.isGrey(obj));
// 	VM.sysWrite(obj);
// 	VM.sysWrite(" ");
// 	SimpleRCHeader.print(obj);
// 	VM.sysWrite(" markgrey\n");
	rcCollector.markGrey(obj);
	tgt.push(obj);
      } else {
	//if (VM.VerifyAssertions) VM._assert(false);
// 	VM.sysWrite(obj);
// 	VM.sysWrite(" ");
// 	SimpleRCHeader.print(obj);
 	SimpleRCHeader.clearBufferedBit(obj);
// 	if (!SimpleRCHeader.isLiveRC(obj)) {
// 	  VM.sysWrite(" d\n");
// 	} else {
// 	  VM.sysWrite(" l\n");
// 	}
      }
    } 
    cycleBufferAisOpen = !cycleBufferAisOpen;
  }
  private final void doScanPhase() {
    VM_Address obj;
    AddressQueue src = (cycleBufferAisOpen) ? cycleBufferA : cycleBufferB;
    AddressQueue tgt = (cycleBufferAisOpen) ? cycleBufferB : cycleBufferA;
    rcCollector.scanPhase();
    while (!(obj = src.pop()).isZero()) {
      if (VM.VerifyAssertions) VM._assert(!SimpleRCHeader.isGreen(obj));
//       VM.sysWrite(obj);
//       VM.sysWrite(" ");
//       SimpleRCHeader.print(obj);
//       VM.sysWrite(" scan\n");

      rcCollector.scan(obj);
      tgt.push(obj);
    }
    cycleBufferAisOpen = !cycleBufferAisOpen;
  }
  private final void doCollectPhase() {
    VM_Address obj;
    AddressQueue src = (cycleBufferAisOpen) ? cycleBufferA : cycleBufferB;
    rcCollector.collectPhase();
    while (!(obj = src.pop()).isZero()) {
      if (VM.VerifyAssertions) VM._assert(!SimpleRCHeader.isGreen(obj));
      SimpleRCHeader.clearBufferedBit(obj);
//       VM.sysWrite(obj);
//       VM.sysWrite(" ");
//       SimpleRCHeader.print(obj);
//       VM.sysWrite(" collect\n");
      rcCollector.collectWhite(obj, this);
    }
    //    processFreeBufs();
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //
  private SimpleRCAllocator rc;
  private BumpPointer immortal;
  private int id;  
  
  private int incCounter;
  private int decCounter;
  private int rootCounter;
  private int purpleCounter;
  private int wbFastPathCounter;

  private AddressQueue incBuffer;
  private AddressQueue decBuffer;
  private AddressQueue rootSet;
  private AddressQueue cycleBufferA;
  private AddressQueue cycleBufferB;
  private AddressQueue freeBuffer;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  // virtual memory regions
  private static SimpleRCCollector rcCollector;
  private static FreeListVMResource rcVM;
  private static ImmortalVMResource immortalVM;

  // memory resources
  private static MemoryResource rcMR;
  private static MemoryResource immortalMR;

  private static SharedQueue incPool;
  private static SharedQueue decPool;
  private static SharedQueue rootPool;
  private static SharedQueue cyclePoolA;
  private static SharedQueue cyclePoolB;
  private static SharedQueue freePool;

  // GC state
  private static boolean progress = true;  // are we making progress?
  private static int required;  // how many pages must this GC yeild?
  private static boolean cycleBufferAisOpen = true;

  //
  // Final class variables (aka constants)
  //
  private static final VM_Address       RC_START = PLAN_START;
  private static final EXTENT            RC_SIZE = 1024 * 1024 * 1024;              // size of each space
  private static final VM_Address         RC_END = RC_START.add(RC_SIZE);
  private static final VM_Address       HEAP_END = RC_END;

  private static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;

  public static final int RC_ALLOCATOR = 0;
  public static final int IMMORTAL_ALLOCATOR = 1;
  public static final int DEFAULT_ALLOCATOR = RC_ALLOCATOR;
  public static final int TIB_ALLOCATOR = DEFAULT_ALLOCATOR;


  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).
   */
  static {
    // memory resources
    rcMR = new MemoryResource(POLL_FREQUENCY);
    immortalMR = new MemoryResource(POLL_FREQUENCY);

    // virtual memory resources
    rcVM       = new FreeListVMResource("RC",       RC_START,   RC_SIZE, VMResource.MOVABLE);
    immortalVM = new ImmortalVMResource("Immortal", immortalMR, IMMORTAL_START, IMMORTAL_SIZE, BOOT_END);

    // collectors
    rcCollector = new SimpleRCCollector(rcVM, rcMR);

    incPool = new SharedQueue(metaDataRPA, 1);
    incPool.newClient();
    decPool = new SharedQueue(metaDataRPA, 1);
    decPool.newClient();
    rootPool = new SharedQueue(metaDataRPA, 1);
    rootPool.newClient();
    if (refCountCycleDetection) {
      cyclePoolA = new SharedQueue(metaDataRPA, 1);
      cyclePoolA.newClient();
      cyclePoolB = new SharedQueue(metaDataRPA, 1);
      cyclePoolB.newClient();
      freePool = new SharedQueue(metaDataRPA, 1);
      freePool.newClient();
    }
  }

}

