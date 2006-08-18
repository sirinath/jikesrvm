/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package com.ibm.JikesRVM.mm.mmtk;

import org.mmtk.plan.Plan;
import org.mmtk.plan.CollectorContext;
import org.mmtk.plan.MutatorContext;
import org.mmtk.utility.Constants;
import org.mmtk.utility.Finalizer;
import org.mmtk.utility.heap.HeapGrowthManager;
import org.mmtk.utility.ReferenceProcessor;
import org.mmtk.utility.options.Options;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_CompiledMethod;
import com.ibm.JikesRVM.VM_CompiledMethods;
import com.ibm.JikesRVM.VM_Constants;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_Processor;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.VM_Thread;
import com.ibm.JikesRVM.VM_Time;
import com.ibm.JikesRVM.classloader.VM_Atom;
import com.ibm.JikesRVM.classloader.VM_Method;
import com.ibm.JikesRVM.memoryManagers.mmInterface.VM_CollectorThread;
import com.ibm.JikesRVM.memoryManagers.mmInterface.MM_Interface;
import com.ibm.JikesRVM.memoryManagers.mmInterface.SelectedPlan;
import com.ibm.JikesRVM.memoryManagers.mmInterface.SelectedCollectorContext;
import com.ibm.JikesRVM.memoryManagers.mmInterface.SelectedMutatorContext;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * $Id: Collection.java,v 1.8 2006/06/23 06:02:04 steveb-oss Exp $ 
 *
 * @author Steve Blackburn
 * @author Perry Cheng
 *
 * @version $Revision: 1.8 $
 * @date $Date: 2006/06/23 06:02:04 $
 */
public class Collection extends org.mmtk.vm.Collection implements Constants, VM_Constants, Uninterruptible {

  /****************************************************************************
   *
   * Class variables
   */

  /** An unknown GC trigger reason.  Signals a logic bug. */ 
  public static final int UNKNOWN_GC_TRIGGER = 0;  
  /** Externally triggered garbage collection (eg call to System.gc())  */
  public static final int EXTERNAL_GC_TRIGGER = 1;
  /** Resource triggered garbage collection.  For example, an
      allocation request would take the number of pages in use beyond
      the number available. */
  public static final int RESOURCE_GC_TRIGGER = 2;
  /**
   * Internally triggered garbage collection.  For example, the memory
   * manager attempting another collection after the first failed to
   * free space.
   */
  public static final int INTERNAL_GC_TRIGGER = 3;
  /** The number of garbage collection trigger reasons. */
  public static final int TRIGGER_REASONS = 4;
  /** Short descriptions of the garbage collection trigger reasons. */
  private static final String[] triggerReasons = {
    "unknown",
    "external request",
    "resource exhaustion",
    "internal request"
  };

  /** The fully qualified name of the collector thread. */
  private static VM_Atom collectorThreadAtom;
  /** The string "run". */
  private static VM_Atom runAtom;

  /**
   * The percentage threshold for throwing an OutOfMemoryError.  If,
   * after a garbage collection, the amount of memory used as a
   * percentage of the available heap memory exceeds this percentage
   * the memory manager will throw an OutOfMemoryError.
   */
  public static final double OUT_OF_MEMORY_THRESHOLD = 0.98;

  /***********************************************************************
   *
   * Initialization
   */

  /**
   * Initialization that occurs at <i>build</i> time.  The values of
   * statics at the completion of this routine will be reflected in
   * the boot image.  Any objects referenced by those statics will be
   * transitively included in the boot image.
   *
   * This is called from MM_Interface.
   */
  public static final void init() throws InterruptiblePragma {
    collectorThreadAtom = VM_Atom.findOrCreateAsciiAtom(
      "Lcom/ibm/JikesRVM/memoryManagers/mmInterface/VM_CollectorThread;");
    runAtom = VM_Atom.findOrCreateAsciiAtom("run");
  }

  /**
   * An enumerator used to forward root objects
   */
  /**
   * Triggers a collection.
   *
   * @param why the reason why a collection was triggered.  0 to
   * <code>TRIGGER_REASONS - 1</code>.
   */
  public final void triggerCollection(int why) throws InterruptiblePragma {
    triggerCollectionStatic(why);
  }
  
  public static final void triggerCollectionStatic(int why) throws InterruptiblePragma {
    if (VM.VerifyAssertions) VM._assert((why >= 0) && (why < TRIGGER_REASONS)); 
    Plan.collectionInitiated();

    if (Options.verbose.getValue() >= 4) {
      VM.sysWriteln("Entered VM_Interface.triggerCollection().  Stack:");
      VM_Scheduler.dumpStack();
    }
    if (why == EXTERNAL_GC_TRIGGER) {
      SelectedPlan.get().userTriggeredGC();
      if (Options.verbose.getValue() == 1 || Options.verbose.getValue() == 2) 
        VM.sysWrite("[Forced GC]");
    }
    if (Options.verbose.getValue() > 2) 
      VM.sysWriteln("Collection triggered due to ", triggerReasons[why]);
    Extent sizeBeforeGC = HeapGrowthManager.getCurrentHeapSize();
    long start = VM_Time.cycles();
    VM_CollectorThread.collect(VM_CollectorThread.handshake, why);
    long end = VM_Time.cycles();
    double gcTime = VM_Time.cyclesToMillis(end - start);
    if (Options.verbose.getValue() > 2) VM.sysWriteln("Collection finished (ms): ", gcTime);

    if (SelectedPlan.get().isLastGCFull() && 
   sizeBeforeGC.EQ(HeapGrowthManager.getCurrentHeapSize()))
      checkForExhaustion(why, false);
    
    Plan.checkForAsyncCollection();
  }

  /**
   * Triggers a collection without allowing for a thread switch.  This is needed
   * for Merlin lifetime analysis used by trace generation 
   *
   * @param why the reason why a collection was triggered.  0 to
   * <code>TRIGGER_REASONS - 1</code>.
   */
  public final void triggerCollectionNow(int why) 
    throws LogicallyUninterruptiblePragma {
    if (VM.VerifyAssertions) VM._assert((why >= 0) && (why < TRIGGER_REASONS)); 
    Plan.collectionInitiated();

    if (Options.verbose.getValue() >= 4) {
      VM.sysWriteln("Entered VM_Interface.triggerCollectionNow().  Stack:");
      VM_Scheduler.dumpStack();
    }
    if (why == EXTERNAL_GC_TRIGGER) {
      SelectedPlan.get().userTriggeredGC();
      if (Options.verbose.getValue() == 1 || Options.verbose.getValue() == 2) 
        VM.sysWrite("[Forced GC]");
    }
    if (Options.verbose.getValue() > 2) 
      VM.sysWriteln("Collection triggered due to ", triggerReasons[why]);
    Extent sizeBeforeGC = HeapGrowthManager.getCurrentHeapSize();
    long start = VM_Time.cycles();
    VM_CollectorThread.collect(VM_CollectorThread.handshake, why);
    long end = VM_Time.cycles();
    double gcTime = VM_Time.cyclesToMillis(end - start);
    if (Options.verbose.getValue() > 2) 
      VM.sysWriteln("Collection finished (ms): ", gcTime);

    if (SelectedPlan.get().isLastGCFull() && 
        sizeBeforeGC.EQ(HeapGrowthManager.getCurrentHeapSize()))
      checkForExhaustion(why, false);
    
    Plan.checkForAsyncCollection();
  }

  /**
   * Trigger an asynchronous collection, checking for memory
   * exhaustion first.
   */
  public final void triggerAsyncCollection()
    throws UninterruptiblePragma {
    checkForExhaustion(RESOURCE_GC_TRIGGER, true);
    Plan.collectionInitiated();
    if (Options.verbose.getValue() >= 1) VM.sysWrite("[Async GC]");
    VM_CollectorThread.asyncCollect(VM_CollectorThread.handshake);
  }

  /**
   * Determine whether a collection cycle has fully completed (this is
   * used to ensure a GC is not in the process of completing, to
   * avoid, for example, an async GC being triggered on the switch
   * from GC to mutator thread before all GC threads have switched.
   *
   * @return True if GC is not in progress.
   */
 public final boolean noThreadsInGC() throws UninterruptiblePragma {
   return VM_CollectorThread.noThreadsInGC(); 
 }

  private static final int OOM_EXN_HEADROOM_BYTES = 1<<19; // 512K should be plenty to make an exn
  /**
   * Check for memory exhaustion, possibly throwing an out of memory
   * exception and/or triggering another GC.
   *
   * @param why Why the collection was triggered
   * @param async True if this collection was asynchronously triggered.
   */
  private static final void checkForExhaustion(int why, boolean async)
    throws LogicallyUninterruptiblePragma {
    double usage = Plan.reservedMemory().toLong()/ ((double) Plan.totalMemory().toLong());
    
    //    if (Plan.totalMemory() - Plan.reservedMemory() < 64<<10) {
    if (usage > OUT_OF_MEMORY_THRESHOLD) {
      if (why == INTERNAL_GC_TRIGGER) {
        if (Options.verbose.getValue() >= 2) {
          VM.sysWriteln("OutOfMemoryError: usage = ", usage);
          VM.sysWriteln("          reserved (KB) = ",(long)(Plan.reservedMemory().toLong() / 1024));
          VM.sysWriteln("          total    (KB) = ",(long)(Plan.totalMemory().toLong() / 1024));
        }
        if (VM.debugOOM || Options.verbose.getValue() >= 5)
          VM.sysWriteln("triggerCollection(): About to try \"new OutOfMemoryError()\"");
        
        int currentAvail = SelectedPlan.get().getPagesAvail() << LOG_BYTES_IN_PAGE; // may be negative
        int headroom = OOM_EXN_HEADROOM_BYTES - currentAvail;
        MM_Interface.emergencyGrowHeap(headroom);  
        OutOfMemoryError oome = new OutOfMemoryError();
        MM_Interface.emergencyGrowHeap(-headroom);
        if (VM.debugOOM || Options.verbose.getValue() >= 5)
          VM.sysWriteln("triggerCollection(): Allocated the new OutOfMemoryError().");
        throw oome;
      }
      /* clear all possible reference objects */
      ReferenceProcessor.setClearSoftReferences(true);
      if (!async)
        triggerCollectionStatic(INTERNAL_GC_TRIGGER);
    }
  }

  /**
   * Prepare a mutator for a collection.
   *
   * @param m the mutator to prepare
   */
  public final void prepareMutator(MutatorContext m) {
    /*
     * The collector threads of processors currently running threads
     * off in JNI-land cannot run.
     */
    VM_Processor vp = ((SelectedMutatorContext) m).getProcessor();
    int vpStatus = vp.vpStatus;
    if (vpStatus == VM_Processor.BLOCKED_IN_NATIVE) {
      
      /* processor & its running thread are blocked in C for this GC.
       Its stack needs to be scanned, starting from the "top" java
       frame, which has been saved in the running threads JNIEnv.  Put
       the saved frame pointer into the threads saved context regs,
       which is where the stack scan starts. */
      VM_Thread t = vp.activeThread;
      t.contextRegisters.setInnermost(Address.zero(), t.jniEnv.topJavaFP());
    }
  }
  
  /**
   * Prepare a collector for a collection.
   *
   * @param c the collector to prepare
   */
  public final void prepareCollector(CollectorContext c) {
    VM_Processor vp = ((SelectedCollectorContext) c).getProcessor();
    int vpStatus = vp.vpStatus;
    if (VM.VerifyAssertions) VM._assert(vpStatus != VM_Processor.BLOCKED_IN_NATIVE);
    VM_Thread t = VM_Thread.getCurrentThread();
    Address fp = VM_Magic.getFramePointer();
    while (true) {
      Address caller_ip = VM_Magic.getReturnAddress(fp);
      Address caller_fp = VM_Magic.getCallerFramePointer(fp);
      if (VM_Magic.getCallerFramePointer(caller_fp).EQ(STACKFRAME_SENTINEL_FP)) 
        VM.sysFail("prepareMutator (participating): Could not locate VM_CollectorThread.run");
      int compiledMethodId = VM_Magic.getCompiledMethodID(caller_fp);
      VM_CompiledMethod compiledMethod = VM_CompiledMethods.getCompiledMethod(compiledMethodId);
      VM_Method method = compiledMethod.getMethod();
      VM_Atom cls = method.getDeclaringClass().getDescriptor();
      VM_Atom name = method.getName();
      if (name == runAtom && cls == collectorThreadAtom) {
        t.contextRegisters.setInnermost(caller_ip, caller_fp);
        break;
      }
      fp = caller_fp; 
    }
  }

  /**
   * Rendezvous with all other processors, returning the rank
   * (that is, the order this processor arrived at the barrier).
   */
  public final int rendezvous(int where) throws UninterruptiblePragma {
    return VM_CollectorThread.gcBarrier.rendezvous(where);
  }

  /***********************************************************************
   *
   * Finalizers
   */
  
  /**
   * Schedule the finalizerThread, if there are objects to be
   * finalized and the finalizerThread is on its queue (ie. currently
   * idle).  Should be called at the end of GC after moveToFinalizable
   * has been called, and before mutators are allowed to run.
   */
  public static final void scheduleFinalizerThread ()
    throws UninterruptiblePragma {

    int finalizedCount = Finalizer.countToBeFinalized();
    boolean alreadyScheduled = VM_Scheduler.finalizerQueue.isEmpty();
    if (finalizedCount > 0 && !alreadyScheduled) {
      VM_Thread t = VM_Scheduler.finalizerQueue.dequeue();
      VM_Processor.getCurrentProcessor().scheduleThread(t);
    }
  }
}
