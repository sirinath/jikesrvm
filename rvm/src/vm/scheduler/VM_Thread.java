/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

/**
 *
 * A java thread's execution context.
 * 28 May 1998 Derek Lieber
 */
public class VM_Thread implements VM_Constants, VM_BaselineConstants, VM_Uninterruptible {

  // following for RCGC reference counting per thread stack increment/decrement buffers 
  final static int MAX_STACKBUFFER_COUNT = 2;

  // debup flags
  private final static boolean trace = false;
  private final static boolean debugDeadVP = false;

  // enumerate different types of yield points for sampling
  final static int PROLOGUE = 0;
  final static int BACKEDGE = 1;
  final static int EPILOGUE = 2;
  
  //------------------//
  // Public Interface //
  //------------------//

  // Create a thread with default stack.
  //
  public VM_Thread () {
    this(new int[STACK_SIZE_NORMAL >> 2]);
  }

  // Get current thread.
  //
  public static VM_Thread getCurrentThread () {
    return VM_Processor.getCurrentProcessor().activeThread;
  }
      
  public VM_JNIEnvironment getJNIEnv() {
    return jniEnv;
  }

  // Indicate whether the stack of this VM_Thread contains any C frame
  // (used in VM_Runtime.deliverHardwareException for stack resize)
  // Return false during the prolog of the first Java to C transition
  //        true afterward
  public boolean hasNativeStackFrame() {
    if (jniEnv!=null)
      if (jniEnv.alwaysHasNativeFrame || jniEnv.JNIRefsTop!=0)
        return true;
    return false;
  }

  public String
  toString() // overrides Object
     {
     return "VM_Thread";
     }

  // Method to be executed when this thread starts running.
  // Subclass should override with something more interesting.
  //
  public void run () {
  }

  // Method to be executed when this thread termnates.
  // Subclass should override with something more interesting.
  //
  public void exit () {
  }

  // Suspend execution of current thread until it is resumed.
  // Call only if caller has appropriate security clearance.
  //
  public void suspend () {
    suspendLock.lock();
    suspendPending = true;
    suspendLock.unlock();
    if (this == getCurrentThread()) yield();
  }
     
  // Resume execution of a thread that has been suspended.
  // Call only if caller has appropriate security clearance.
  //
  public void resume () {
    suspendLock.lock();
    suspendPending = false;
    if (suspended) { // this thread is not on any queue
      suspended = false;
      suspendLock.unlock();
      VM_Processor.getCurrentProcessor().scheduleThread(this);
    } else {         // this thread is queued somewhere
      suspendLock.unlock();
    }
  }


  // Suspend execution of current thread for specified number of seconds (or fraction).
  //
  public static void sleep (long millis) throws InterruptedException {
    VM_Thread myThread = getCurrentThread();
    myThread.wakeupTime = VM_Time.now() + millis * .001;
    myThread.proxy = new VM_Proxy (myThread, myThread.wakeupTime); // cache the proxy before obtaining lock
    if (VM.BuildForConcurrentGC) {// RCGC - currently prevent threads from migrating to another processor
      myThread.processorAffinity = VM_Processor.getCurrentProcessor();
    }
    VM_Scheduler.wakeupMutex.lock();
    yield(VM_Scheduler.wakeupQueue, VM_Scheduler.wakeupMutex);
  }
   
  // Suspend execution of current thread until "fd" can be read without blocking.
  //
  public static void ioWaitRead (int fd)
     {
  // VM.sysWrite("VM_Thread: ioWaitRead " + fd + "\n");
     VM_Thread myThread   = getCurrentThread();
     myThread.waitFdRead  = fd;
     myThread.waitFdReady = false;
     myThread.waitFdWrite  = -1;
     yield(VM_Processor.getCurrentProcessor().ioQueue);
     }
     
  public static void ioWaitWrite (int fd)
     {
  // VM.sysWrite("VM_Thread: ioWaitRead " + fd + "\n");
     VM_Thread myThread   = getCurrentThread();
     myThread.waitFdRead  = -1;
     myThread.waitFdReady = false;
     myThread.waitFdWrite  = fd;
     yield(VM_Processor.getCurrentProcessor().ioQueue);
     }
     
  // Deliver an exception to this thread.
  //
  public final void kill (Throwable externalInterrupt) {
    this.externalInterrupt = externalInterrupt; // yield() will notice this and take appropriate action
    // remove this thread from wakeup and/or waiting queue
    VM_Proxy p = proxy; 
    if (p != null) {
      VM_Thread t = p.unproxy(); // t == this or t == null
      if (t != null) t.schedule();
    }
    // TODO!! handle this thread executing native code
  }

  // Preempt execution of current thread.
  // Called by compiler-generated yieldpoints approx. every 10ms.
  //
  // NOTE: The ThreadSwitchSampling code depends on there
  // being the same number of wrapper routines for all
  // compilers. Please talk to me (Dave G) before changing this. Thanks.
  // We could try a substantially more complex implementation
  // (especially on the opt side) to avoid the wrapper routine, 
  // for the baseline compiler, but I think this is the easiest way
  // to handle all the cases at reasonable runtime-cost. 
  public static void threadSwitchFromPrologue() {
    threadSwitch(PROLOGUE);
  }
  public static void threadSwitchFromBackedge() {
    threadSwitch(BACKEDGE);
  }
  public static void threadSwitchFromEpilogue() {
    threadSwitch(EPILOGUE);
  }
  public static void threadSwitch(int whereFrom) {
    VM_Magic.pragmaNoInline();
    VM_Magic.clearThreadSwitchBit();
    VM_Processor.getCurrentProcessor().threadSwitchRequested = 0;
    
    if (!VM_Processor.getCurrentProcessor().threadSwitchingEnabled())
       { // thread in critical section: can't switch right now, defer 'till later
       VM_Processor.getCurrentProcessor().threadSwitchPending = true;
       return;
       }
    
    if (VM_Scheduler.debugRequested && VM_Scheduler.allProcessorsInitialized)
       { // service "debug request" generated by external signal
       VM_Scheduler.debuggerMutex.lock();
       if (VM_Scheduler.debuggerQueue.isEmpty())
          { // debugger already running
          VM_Scheduler.debuggerMutex.unlock();
          }
       else
          { // awaken debugger
          VM_Thread t = VM_Scheduler.debuggerQueue.dequeue();
          VM_Scheduler.debuggerMutex.unlock();
          t.schedule();
          }
       }

    if (VM_Scheduler.attachThreadRequested!=0) 
      {
	// service AttachCurrentThread request from an external pthread
	VM_Scheduler.attachThreadMutex.lock();
	if (VM_Scheduler.attachThreadQueue.isEmpty())
	  { // JNIServiceThread already running
	    VM_Scheduler.attachThreadMutex.unlock();
	  }
	else
	  { // awaken JNIServiceThread
	    VM_Thread t = VM_Scheduler.attachThreadQueue.dequeue();
	    VM_Scheduler.attachThreadMutex.unlock();
	    t.schedule();
	  }
      }
    

    if (VM_Scheduler.wakeupQueue.isReady()) {
      VM_Scheduler.wakeupMutex.lock();
      VM_Thread t = VM_Scheduler.wakeupQueue.dequeue();
      VM_Scheduler.wakeupMutex.unlock();
      if (t != null)
         {
      // VM_Scheduler.trace("VM_Thread", "threadSwitch: awaken ", t.getIndex());
         t.schedule();
         }
    }


    // Reset thread switch count for deterministic thread switching
    if(VM.BuildForDeterministicThreadSwitching) 
      VM_Processor.getCurrentProcessor().deterministicThreadSwitchCount = VM.deterministicThreadSwitchInterval;

//-#if RVM_WITH_ADAPTIVE_COMPILER
    // We use threadswitches as a rough approximation of time. 
    // Every threadswitch is a clock tick.
    VM_Controller.controllerClock++;

    //
    // "The idle thread is boring, and does not deserve to be sampled"
    //                           -- AOS Commandment Number 1
    if (!VM_Thread.getCurrentThread().isIdleThread) {

      // First, get the cmid for the method in which the yieldpoint was taken.

      // Get pointer to my caller's frame
      int fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer()); 

      // Skip over wrapper to "real" method
      fp = VM_Magic.getCallerFramePointer(fp);                             
      int ypTakenInCMID = VM_Magic.getCompiledMethodID(fp);

      // Next, get the cmid for that method's caller.
      fp = VM_Magic.getCallerFramePointer(fp);
      int ypTakenInCallerCMID = VM_Magic.getCompiledMethodID(fp);
	
      // Determine if ypTakenInCallerCMID actually corresponds to a real 
      // Java stackframe.
      boolean ypTakenInCallerCMIDValid = true;
      VM_CompiledMethod ypTakenInCM = 
	VM_ClassLoader.getCompiledMethod(ypTakenInCMID);

      if (ypTakenInCM == null) {
	VM.sysWrite("Found a cmid (");
	VM.sysWrite(ypTakenInCMID, false);
	VM.sysWrite(") with a null compiled method, exiting");
	throw new RuntimeException();
      }

      // Check for one of the following:
      //    Caller is top-of-stack psuedo-frame
      //    Caller is out-of-line assembly (no VM_Method object)
      //    Caller is a native method
      if (ypTakenInCallerCMID == STACKFRAME_SENTINAL_FP ||
	  ypTakenInCallerCMID == INVISIBLE_METHOD_ID    ||
	  ypTakenInCM.getMethod().getDeclaringClass().isBridgeFromNative()) { 
	ypTakenInCallerCMIDValid = false;
      } 
      
      // Now that we have the basic information we need, 
      // notify all currently registered listeners
      if (VM_RuntimeMeasurements.hasMethodListener()){
        // set the Caller CMID to -1 if invalid
        if (!ypTakenInCallerCMIDValid) ypTakenInCallerCMID = -1;  

	if (ypTakenInCallerCMID != -1) {
	  if (VM_ClassLoader.getCompiledMethod(ypTakenInCallerCMID) == null) {
	    VM.sysWrite("Found a caller cmid (");
	    VM.sysWrite(ypTakenInCallerCMID, false);
	    VM.sysWrite(") with a null compiled method, exiting");
	    throw new RuntimeException();
	  }
	}

	if (VM_ClassLoader.getCompiledMethod(ypTakenInCMID) == null) {
	  VM.sysWrite("Found a cmid (");
	  VM.sysWrite(ypTakenInCMID, false);
	  VM.sysWrite(") with a null compiled method, exiting");
	  throw new RuntimeException();
	}

	VM_RuntimeMeasurements.activateMethodListeners(ypTakenInCMID,
                                                       ypTakenInCallerCMID, 
                                                       whereFrom);
      }

      if (ypTakenInCallerCMIDValid && 
	  VM_RuntimeMeasurements.hasContextListener()) {
	// Have to start over again in case an intervening GC has moved fp 
	//    since the last time we did this.

	// Get pointer to my caller's frame
	fp = VM_Magic.getCallerFramePointer(VM_Magic.getFramePointer());

	// Skip over wrapper to "real" method
	fp = VM_Magic.getCallerFramePointer(fp);                         
	VM_RuntimeMeasurements.activateContextListeners(fp, whereFrom);

      }

      if (VM_RuntimeMeasurements.hasNullListener()){
	VM_RuntimeMeasurements.activateNullListeners(whereFrom);
      }
    }
//-#endif
       
    // VM_Scheduler.trace("VM_Thread", "threadSwitch");
    yield();
  }

  // Suspend execution of current thread, in favor of some other thread.
  //!!TODO: verify that this method gets inlined by opt compiler
  //
  public static void yield () {
    VM_Thread myThread = getCurrentThread();
    myThread.beingDispatched = true;
    VM_Processor.getCurrentProcessor().readyQueue.enqueue(myThread);
    morph();
  }

  // Suspend execution of current thread in favor of some other thread.
  // Taken: queue to put thread onto (must be processor-local, ie. not guarded with a lock)
  //!!TODO: verify that this method gets inlined by opt compiler
  //
  static void yield (VM_AbstractThreadQueue q) {
    VM_Thread myThread = getCurrentThread();
    myThread.beingDispatched = true;
    q.enqueue(myThread);
    morph();
  }
  
  // Suspend execution of current thread in favor of some other thread.
  // Taken: queue to put thread onto
  //        lock guarding that queue (currently locked)
  //!!TODO: verify that this method gets inlined by opt compiler
  //
  static void yield (VM_AbstractThreadQueue q, VM_ProcessorLock l) {
    VM_Thread myThread = getCurrentThread();
    myThread.beingDispatched = true;
    q.enqueue(myThread);
    l.unlock();
    morph();
  }

//-#if RVM_WITH_DEDICATED_NATIVE_PROCESSORS
// alternate implementation of jni
//-#else
// default implementation of jni

  // Suspend execution of current thread in favor of some other thread.
  // Taken: VM_Processor of Native processor.
  //
  // Place current thread onto transfer queue of native processor.
  // Unblock that processor by changing vpStatus to IN_NATIVE (from BLOCKED_IN_NATIVE)
  // morph() so that executing os thread starts executing other java
  // threads in the queues of the current processor
  //
  // XXX WHAT IF...
  // Java thread, once unblocked, completes the yield to a RVM Processor
  // transfer queue, and native processor pthread tries to find work in its
  // queues, and the native idle thread is in its transfer queue BUT its
  // beingDispatched flag is still on because... Will the dispatch logic of
  // the native processor get upset, for ex. skip the idle thread in the
  // transfer queue, look at its idle queue, and find it empty, and barf???
  //
  static void yield (VM_Processor p) {
    VM_Thread myThread = getCurrentThread();
    if (VM.VerifyAssertions) {
      VM.assert(p.processorMode==VM_Processor.NATIVE);
      VM.assert(VM_Processor.vpStatus[p.vpStatusIndex]==VM_Processor.BLOCKED_IN_NATIVE);
      VM.assert(myThread.isNativeIdleThread==true);
    }
    myThread.beingDispatched = true;
    p.transferMutex.lock();
    p.transferQueue.enqueue(myThread);
    VM_Processor.vpStatus[p.vpStatusIndex] = VM_Processor.IN_NATIVE;
    p.transferMutex.unlock();
    morph();
  }
//-#endif

  // For timed wait
  //
  static void yield (VM_ProxyWaitingQueue q1, VM_ProcessorLock l1, VM_ProxyWakeupQueue q2, VM_ProcessorLock l2) {
    VM_Thread myThread = getCurrentThread();
    myThread.beingDispatched = true;
    q1.enqueue(myThread.proxy); // proxy has been cached before locks were obtained
    q2.enqueue(myThread.proxy); // proxy has been cached before locks were obtained
    l1.unlock();
    l2.unlock();
    morph();
  }

  // Current thread has been placed onto some queue. Become another thread.
  //!!TODO: verify that this method gets inlined by opt compiler
  //
  static void morph () {
    //VM_Scheduler.trace("VM_Thread", "morph");
    VM_Thread myThread = getCurrentThread();

    if (VM.VerifyAssertions) VM.assert(VM_Processor.getCurrentProcessor().threadSwitchingEnabled());
    if (VM.VerifyAssertions) VM.assert(myThread.beingDispatched == true);

    // become another thread
    //
    VM_Magic.saveThreadState(myThread.contextRegisters);
    VM_Processor.getCurrentProcessor().dispatch();

    // return from thread switch

    // respond to interrupt sent to this thread by some other thread
    //
    if (myThread.externalInterrupt != null)
       {
       Throwable t = myThread.externalInterrupt;
       myThread.externalInterrupt = null;
       VM_Runtime.athrow(t);
       }
  }

  // transfer execution of the current thread to a "nativeAffinity"
  // Processor (system thread).  Used when making transitions from
  // java to native C (call to native from java or return to native
  // from java.
  //
  // After the yield, we are in a native processor (avoid method calls)
  //
  static void becomeNativeThread () {

    int lockoutId;

    if (trace) {
      VM.sysWrite("VM_Thread.becomeNativeThread entry -process = ");
      VM.sysWrite(VM_Magic.objectAsAddress(VM_Processor.getCurrentProcessor()));
      VM.sysWrite("\n");
      VM.sysWrite("Thread id ");
      VM.sysWrite(VM_Magic.getThreadId() );
      VM.sysWrite("\n");
    }

    VM_Processor p = VM_Thread.getCurrentThread().nativeAffinity;
    if ( p == null) {
      //
      // BE CAREFUL HERE - this commented out code may be required for what is
      // now called the "alternate jni implementation" ie. the old red/blue implementation
      // see SteveS before removing
      //
      // shouldn't happen since the C caller must have recorded nativeAffinity already
      VM.sysWrite("null nativeAffinity, should have been recorded by C caller\n");
      VM.assert(VM.NOT_REACHED);
      // // obtain a native virtual processor
      // if (debugDeadVP) VM.sysWrite("  VM_Thread.becomeNativeThread-about to dequeue from dead VP queue \n ");
      // p = VM_Scheduler.deadVPQueue.dequeue();         // is there a Native VP available?
      // if (debugDeadVP){
      //       VM.sysWrite(" VM_Thread.becomeNativeThread- dequeue from dead VP queue returned");
      //       VM.sysWrite("\n");
      // }
      // if (p == null) {
      //   if (debugDeadVP)
      //     VM.sysWrite(" VM_Thread.becomeNativeThread- dequeue from dead VP queue returned null, create VP \n ");
      //   p =  VM_Processor.createNativeProcessor();    // create a Native VP
      // }
      // // set up processor affinities
      // VM_Thread.getCurrentThread().nativeAffinity = p;
    }
    
    // // Acquire global lockout field (at fixed address in the boot record).
    // // This field will be released by this thread when it runs on the native 
    // // processor (after the yield).
    // int lockoutAddr = VM_Magic.objectAsAddress(VM_BootRecord.the_boot_record) + VM_Entrypoints.lockoutProcessorOffset;
    // //    int lockoutId   = VM_Magic.objectAsAddress(VM_Magic.getProcessorRegister());
    // 
    // // get the lockout lock .. yield if someone else has it
    // 	 while (true) {
    // 	   int lockoutVal = VM_Magic.prepare(lockoutAddr);
    // 	   if ( lockoutVal == 0){
    // 	     lockoutId   = VM_Magic.objectAsAddress(VM_Magic.getProcessorRegister());
    // 	     if(VM_Magic.attempt(lockoutAddr, 0, lockoutId))
    // 	       break;
    // 	   }else yield();
    // 	 }
  

    //// VM_Processor p = VM_Thread.getCurrentThread().nativeAffinity;
    //// if ( p == null) {
    ////   p =  VM_Processor.createNativeProcessor(); 
    ////   VM_Thread.getCurrentThread().nativeAffinity = p;
    //// }

    // ship the thread to the native processor
    p.transferMutex.lock();
    
    VM.sysCall1(VM_BootRecord.the_boot_record.sysPthreadSignalIP, p.pthread_id);

    yield(p.transferQueue, p.transferMutex); // morph to native processor


    // if (VM_Magic.getMemoryWord(lockoutAddr) != lockoutId)
    //   VM_Scheduler.trace("!!!bad lock contents", " contents =", VM_Magic.getMemoryWord(lockoutAddr));
    
    if (trace){
      VM.sysWrite("VM_Thread.becomeNativeThread exit -process = ");
      VM.sysWrite(VM_Magic.objectAsAddress(VM_Processor.getCurrentProcessor()));
      VM.sysWrite("\n");
    }
  }

  // Until the yield, we are in a native processor (avoid method calls)
  static void becomeRVMThread () {

    VM_Magic.getProcessorRegister().activeThread.returnAffinity.transferMutex.lock();
    yield( VM_Thread.getCurrentThread().returnAffinity.transferQueue,  VM_Thread.getCurrentThread().returnAffinity.transferMutex); // morph to RVM processor
    
    if (trace) {
      VM.sysWrite("VM_Thread.becomeRVMThread- exit process = ");
      VM.sysWrite(VM_Magic.objectAsAddress(VM_Processor.getCurrentProcessor()));
      VM.sysWrite("\n");
    }

  }

  //----------------------------------//
  // Interface to scheduler subsystem //
  //----------------------------------//

  // Put this thread on ready queue for subsequent execution on a future timeslice.
  // Assumption: VM_Thread.contextRegisters are ready to pick up execution
  //             ie. return to a yield or begin thread startup code
  //
  // !!TODO: consider having an argument to schedule() that tells what priority
  //         to give the thread. Then eliminate scheduleHighPriority().
  //
  
  public final void schedule () {
//VM_Scheduler.trace("VM_Thread", "schedule", getIndex());
  VM_Processor.getCurrentProcessor().scheduleThread(this);
  }

  // Put this thread at the front of the ready queue for subsequent execution on a future timeslice.
  // Assumption: VM_Thread.contextRegisters are ready to pick up execution
  //             ie. return to a yield or begin thread startup code
  // !!TODO: this method is a no-op, stop using it
  //
  public final void scheduleHighPriority () {
//VM_Scheduler.trace("VM_Thread", "scheduleHighPriority", getIndex());
  VM_Processor.getCurrentProcessor().scheduleThread(this);
  }

  // Begin execution of current thread by calling its "run" method.
  //
  private static void startoff () {
  //VM_Scheduler.trace("VM_Thread", "startoff");
    VM_Thread currentThread = getCurrentThread();
    currentThread.run();
    terminate();
    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
  }
 
  // Terminate execution of current thread by abandoning all references to it and
  // resuming execution in some other (ready) thread.
  //
  static void terminate () {
    boolean terminateSystem = false;

  //VM_Scheduler.trace("VM_Thread", "terminate");
    VM_Thread myThread = getCurrentThread();
    myThread.exit(); // allow java.lang.Thread.exit() to remove this thread from ThreadGroup
    synchronized (myThread)
       { // release anybody waiting on this thread - in particular, see java.lang.Thread.join()
       myThread.isAlive = false;
       myThread.notifyAll();
       }

    //-#if RVM_WITH_ADAPTIVE_COMPILER
    if (VM.BuildForCpuMonitoring) VM_RuntimeMeasurements.monitorThreadExit();
    //-#endif

    // begin critical section
    //
    VM_Scheduler.threadCreationMutex.lock();
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    
    VM_Scheduler.numActiveThreads -= 1;
    if (myThread.isDaemon)
      VM_Scheduler.numDaemons -= 1;
    if (VM_Scheduler.numDaemons == VM_Scheduler.numActiveThreads) {
      // no non-daemon thread remains
      terminateSystem = true;
    }

    // end critical section
    //
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
    VM_Scheduler.threadCreationMutex.unlock();
    if (VM.VerifyAssertions) VM.assert(VM_Processor.getCurrentProcessor().threadSwitchingEnabled());


    if (terminateSystem) {
      VM.sysExit(0);
      if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
    }


    //add Native Thread Virtual Processor to dead VP queue
    VM_Processor p  = myThread.nativeAffinity;          // check for  native processor
    if ( p != null) {
      VM_Scheduler.deadVPQueue.enqueue(p);                  // put VP on dead queue
      myThread.nativeAffinity    = null;          // clear native processor
      myThread.processorAffinity = null;          // clear processor affinity
      //-#if RVM_WITH_PURPLE_VPS
      VM_Processor.vpStatus[p.vpStatusIndex] = VM_Processor.RVM_VP_GOING_TO_WAIT;
      //-#endif
    }   


    // become another thread
    // begin critical section
    //
    VM_Scheduler.threadCreationMutex.lock();
    myThread.beingDispatched = true;
    VM_Scheduler.deadQueue.enqueue(myThread);
    VM_Scheduler.threadCreationMutex.unlock();


    VM_Magic.saveThreadState(myThread.contextRegisters);
    VM_Processor.getCurrentProcessor().dispatch();

    if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
  }
  
  // Get this thread's index in VM_Scheduler.threads[]
  //
  public final int getIndex() { return threadSlot; }
  
  // Get this thread's id for use in lock ownership tests.
  //
  final int getLockingId() { return threadSlot << OBJECT_THREAD_ID_SHIFT; }
  
  //------------------------------------------//
  // Interface to memory management subsystem //
  //------------------------------------------//

   private static final boolean traceAdjustments = false;
  
  // Change size of currently executing thread's stack.
  // Taken:    new size (in words)
  //           register state at which stack overflow trap was encountered (null --> normal method call, not a trap)
  // Returned: nothing (caller resumes execution on new stack)
  //
  public static void
  resizeCurrentStack(int newSize, VM_Registers exceptionRegisters)
     {
     if (traceAdjustments) VM.sysWrite("VM_Thread: resizeCurrentStack\n");
     if (VM_Collector.gcInProgress())
       VM.sysFail("system error: resizing stack while GC is in progress");
     int[] newStack = new int[newSize];
     VM_Processor.getCurrentProcessor().disableThreadSwitching();
     transferExecutionToNewStack(newStack, exceptionRegisters);
     VM_Processor.getCurrentProcessor().enableThreadSwitching();
     if (traceAdjustments) {
	 VM.sysWrite("VM_Thread: resized stack ");
	 VM.sysWrite(getCurrentThread().getIndex());
	 VM.sysWrite(" to ");
	 VM.sysWrite(((getCurrentThread().stack.length << 2)/1024));
	 VM.sysWrite("k\n");
     }
     }

  private static void
  transferExecutionToNewStack(int[] newStack, VM_Registers exceptionRegisters)
     {
     // prevent opt compiler from inlining a method that contains a magic
     // (returnToNewStack) that it does not implement.
     VM_Magic.pragmaNoInline(); 

     VM_Thread myThread = getCurrentThread();
     int[]     myStack  = myThread.stack;

     // initialize new stack with live portion of stack we're currently running on
     //
     //  lo-mem                                        hi-mem
     //                           |<---myDepth----|
     //                +----------+---------------+
     //                |   empty  |     live      |
     //                +----------+---------------+
     //                 ^myStack   ^myFP           ^myTop
     //
     //       +-------------------+---------------+
     //       |       empty       |     live      |
     //       +-------------------+---------------+
     //        ^newStack           ^newFP          ^newTop
     //
     int myTop   = VM_Magic.objectAsAddress(myStack)  + (myStack.length  << 2);
     int newTop  = VM_Magic.objectAsAddress(newStack) + (newStack.length << 2);

     int myFP    = VM_Magic.getFramePointer();
     int myDepth = myTop - myFP;
     int newFP   = newTop - myDepth;

     // The frame pointer addresses the top of the frame on powerpc and the bottom
     // on intel.  if we copy the stack up to the current frame pointer in here, the
     // copy will miss the header of the intel frame.  Thus we make another call
     // to force the copy.  A more explicit way would be to up to the frame pointer
     // and the header for intel.
     int delta = copyStack(newStack);

     // fix up registers and save areas so they refer to "newStack" rather than "myStack"
     //
     if (exceptionRegisters != null)
        adjustRegisters(exceptionRegisters, delta);
     adjustStack(newStack, newFP, delta);
     
     // install new stack
     //
     myThread.stack      = newStack;
     myThread.stackLimit = VM_Magic.objectAsAddress(newStack) + STACK_SIZE_GUARD;
     
     // return to caller, resuming execution on new stack (original stack now abandoned)
     //
     //-#if RVM_FOR_POWERPC
     VM_Magic.returnToNewStack(VM_Magic.getCallerFramePointer(newFP));
     //-#endif
     //-#if RVM_FOR_IA32
     VM_Magic.returnToNewStack(newFP);
     //-#endif
     if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
     }

  // This (suspended) thread's stack has been moved.
  // Fixup register and memory references to reflect its new position.
  // Taken:    displacement to be applied to all interior references
  // Returned: nothing
  //
  final void
  fixupMovedStack(int delta)
     {
     if (traceAdjustments) VM.sysWrite("VM_Thread: fixupMovedStack\n");
     
     if (contextRegisters.gprs[FP] != VM_NULL)
       adjustRegisters(contextRegisters, delta);
     if ( (hardwareExceptionRegisters.inuse) &&
	  (hardwareExceptionRegisters.gprs[FP] != VM_NULL) )
       adjustRegisters(hardwareExceptionRegisters, delta);
     if (contextRegisters.gprs[FRAME_POINTER] != VM_NULL)
       adjustStack(stack, contextRegisters.gprs[FRAME_POINTER], delta);
     stackLimit += delta;
     }

  // A thread's stack has been moved or resized.
  // Adjust registers to reflect new position.
  //
  // Taken:    registers to be adjusted
  //           displacement to be applied
  // Returned: nothing
  //
  private static void
  adjustRegisters(VM_Registers registers, int delta)
     {
     if (traceAdjustments) VM.sysWrite("VM_Thread: adjustRegisters\n");
  
     // adjust FP
     //
     registers.gprs[FP] += delta;
     if (traceAdjustments) {
       VM.sysWrite(" fp=");
       VM.sysWrite(registers.gprs[FP]);
     }

     // adjust SP (baseline frames only on PPC)
     //
     int compiledMethodId = VM_Magic.getCompiledMethodID(registers.gprs[FP]);
     if (compiledMethodId != INVISIBLE_METHOD_ID)
        {
        VM_CompiledMethod compiledMethod = 
          VM_ClassLoader.getCompiledMethod(compiledMethodId);
//-#if RVM_FOR_IA32
        {
//-#endif
//-#if RVM_FOR_POWERPC
        if (compiledMethod.getCompilerInfo().getCompilerType() == 
            VM_CompilerInfo.BASELINE) {
//-#endif
           registers.gprs[SP] += delta;
           if (traceAdjustments) {
	       VM.sysWrite(" sp=");
	       VM.sysWrite(registers.gprs[SP]);
	   }
	}
        if (traceAdjustments) {
	    VM.sysWrite(" method=");
	    VM.sysWrite(compiledMethod.getMethod());
	    VM.sysWrite("\n");
        }
     }
	}

  // A thread's stack has been moved or resized.
  // Adjust internal pointers to reflect new position.
  //
  // Taken:    stack to be adjusted
  //           pointer to its innermost frame
  //           displacement to be applied to all its interior references
  // Returned: nothing
  //
  private static void
  adjustStack(int[] stack, int fp, int delta)
     {
     if (traceAdjustments) VM.sysWrite("VM_Thread: adjustStack\n");
    
     while (VM_Magic.getCallerFramePointer(fp) != STACKFRAME_SENTINAL_FP)
        {
        // adjust FP save area
        //
        VM_Magic.setCallerFramePointer(fp, VM_Magic.getCallerFramePointer(fp) + delta);
        if (traceAdjustments) {
	    VM.sysWrite(" fp=");
	    VM.sysWrite(fp);
	}

        // adjust SP save area (baseline frames only)
        //
	//-#if RVM_FOR_POWERPC
        int compiledMethodId = VM_Magic.getCompiledMethodID(fp);
        if (compiledMethodId != INVISIBLE_METHOD_ID)
           {
           VM_CompiledMethod compiledMethod = VM_ClassLoader.getCompiledMethod(compiledMethodId);
           if (compiledMethod.getCompilerInfo().getCompilerType() == VM_CompilerInfo.BASELINE)
              {
              int spOffset = VM_Compiler.getSPSaveAreaOffset(compiledMethod.getMethod());
              VM_Magic.setMemoryWord(fp + spOffset, VM_Magic.getMemoryWord(fp + spOffset) + delta);
              if (traceAdjustments) {
		  VM.sysWrite(" sp=");
		  VM.sysWrite(VM_Magic.getMemoryWord(fp + spOffset));
	      }
              }
           if (traceAdjustments) {
	       VM.sysWrite(" method=");
	       VM.sysWrite(compiledMethod.getMethod());
	       VM.sysWrite("\n");
	   }
           }
	//-#endif

        // advance to next frame
        //
        fp = VM_Magic.getCallerFramePointer(fp);
        }
     }
  
     // initialize new stack with live portion of stack we're currently running on
     //
     //  lo-mem                                        hi-mem
     //                           |<---myDepth----|
     //                +----------+---------------+
     //                |   empty  |     live      |
     //                +----------+---------------+
     //                 ^myStack   ^myFP           ^myTop
     //
     //       +-------------------+---------------+
     //       |       empty       |     live      |
     //       +-------------------+---------------+
     //        ^newStack           ^newFP          ^newTop
     //
  private static int
  copyStack (int[] newStack)
  {
     VM_Thread myThread = getCurrentThread();
     int[]     myStack  = myThread.stack;

     int myTop   = VM_Magic.objectAsAddress(myStack)  + (myStack.length  << 2);
     int newTop  = VM_Magic.objectAsAddress(newStack) + (newStack.length << 2);

     int myFP    = VM_Magic.getFramePointer();
     int myDepth = myTop - myFP;
     int newFP   = newTop - myDepth;

     // before copying, make sure new stack isn't too small
     //
     if (VM.VerifyAssertions)
       VM.assert(newFP >= VM_Magic.objectAsAddress(newStack) + STACK_SIZE_GUARD);

     VM_Memory.aligned32Copy(newFP, myFP, myDepth);

     return newFP - myFP;
  }

  //----------------//
  // Implementation //
  //----------------//

  // Set the "isDaemon" status of this thread.
  // Although a java.lang.Thread can only have setDaemon invoked on it
  // before it is started, VM_Threads can become daemons at any time.
  // Note: making the last non daemon a daemon will terminate the VM. 
  //
  // Note: This method might need to be uninterruptible so it is final,
  // which is why it isn't called setDaemon.
  // 
  protected final void makeDaemon (boolean on) {
    if (isDaemon == on) return;
    VM_Scheduler.threadCreationMutex.lock();
    isDaemon = on;
    if (on) {
      if (++VM_Scheduler.numDaemons == VM_Scheduler.numActiveThreads) {
	if (VM.TraceThreads) VM_Scheduler.trace("VM_Thread", "last non Daemon demonized");
        VM_Scheduler.threadCreationMutex.unlock();
	VM.sysExit(0);
	if (VM.VerifyAssertions) VM.assert(VM.NOT_REACHED);
      }
      else {
        VM_Scheduler.threadCreationMutex.unlock();
      }
    } else {
      --VM_Scheduler.numDaemons;
      VM_Scheduler.threadCreationMutex.unlock();
    }
  }
  
  // Create a thread.
  // Taken: stack in which to execute the thread
  // !!TODO: VM_Scheduler.deadQueue() has a bunch of dead threads that we should
  //         recycle from, before new'ing.
  //
  VM_Thread (int[] stack) {
    this.stack = stack;

    suspendLock = new VM_ProcessorLock();

    contextRegisters           = new VM_Registers();
    hardwareExceptionRegisters = new VM_Registers();

    // RCGC related field initialization
    if (VM.BuildForConcurrentGC) {
	stackBufferNeedScan = true;
	stackBufferSame = false;
	stackBufferCurrent = 0;
	stackBuffer = new int[MAX_STACKBUFFER_COUNT];
	stackBufferTop = new int[MAX_STACKBUFFER_COUNT];
	stackBufferMax = new int[MAX_STACKBUFFER_COUNT];
    }
    
    // put self in list of threads known to scheduler and garbage collector
    // !!TODO: need growable array here
    // !!TODO: must recycle thread ids
    //
    
    if (!VM.runningVM)
       { // create primordial thread (in boot image)
       VM_Scheduler.threads[threadSlot = VM_Scheduler.PRIMORDIAL_THREAD_INDEX] = this; // note that VM_Scheduler.threadAllocationIndex (search hint) is out of date
       VM_Scheduler.numActiveThreads += 1;
       return;
       }
    
    // begin critical section
    //
    VM_Scheduler.threadCreationMutex.lock();
    VM_Processor.getCurrentProcessor().disableThreadSwitching();
    
    assignThreadSlot();
    VM_Scheduler.numActiveThreads += 1;

    if (VM.BuildForConcurrentGC) { // RCGC - currently assign a thread to a processor - no migration yet
      if (VM_Scheduler.allProcessorsInitialized) {
	//-#if RVM_WITH_CONCURRENT_GC // because VM_RCCollectorThread only available for concurrent memory managers
	if (VM_RCCollectorThread.GC_ALL_TOGETHER && VM_Scheduler.numProcessors > 1) {
	  // assign new threads to first N-1 processors, reserve last for gc
	  processorAffinity = VM_Scheduler.processors[(threadSlot % (VM_Scheduler.numProcessors-1)) + 1];
	} else {
	  processorAffinity = VM_Scheduler.processors[(threadSlot % VM_Scheduler.numProcessors) + 1];
	}
	//-#endif
      }
    }

    // end critical section
    //
    VM_Processor.getCurrentProcessor().enableThreadSwitching();
    VM_Scheduler.threadCreationMutex.unlock();


    // create a normal (ie. non-primordial) thread
    //
  //VM_Scheduler.trace("VM_Thread", "create");
      
    stackLimit = VM_Magic.objectAsAddress(stack) + STACK_SIZE_GUARD;

    // make sure thread id will fit in Object .status field
    //
    if (VM.VerifyAssertions) VM.assert(threadSlot == (((threadSlot << OBJECT_THREAD_ID_SHIFT) &(OBJECT_THREAD_ID_MASK) ) >>  OBJECT_THREAD_ID_SHIFT ));

    // get instructions for method to be executed as thread startoff
    //
    VM_Method     method       = (VM_Method)VM.getMember("LVM_Thread;", "startoff", "()V");
    INSTRUCTION[] instructions = method.getMostRecentlyGeneratedInstructions();

    VM.disableGC();

    // initialize thread registers
    //
    int ip = VM_Magic.objectAsAddress(instructions);
    int sp = VM_Magic.objectAsAddress(stack) + (stack.length << 2);
    int fp = STACKFRAME_SENTINAL_FP;

//-#if RVM_FOR_IA32 // TEMP!!

    // initialize thread stack as if "startoff" method had been called
    // by an empty "sentinal" frame with one local variable
    //
    sp -= STACKFRAME_HEADER_SIZE;                   // last word of header
    fp  = sp - WORDSIZE - STACKFRAME_BODY_OFFSET;   // 
    VM_Magic.setCallerFramePointer(fp, STACKFRAME_SENTINAL_FP);
    VM_Magic.setCompiledMethodID(fp, INVISIBLE_METHOD_ID);

    sp -= WORDSIZE;                                 // allow for one local
    contextRegisters.gprs[FRAME_POINTER] = fp;
    contextRegisters.gprs[STACK_POINTER] = sp;
    contextRegisters.gprs[JTOC]          = VM_Magic.objectAsAddress(VM_Magic.getJTOC());
    contextRegisters.fp  = fp;
    contextRegisters.ip  = ip;

//-#else

    // initialize thread stack as if "startoff" method had been called
    // by an empty "sentinal" frame  (with a single argument ???)
    //
    VM_Magic.setMemoryWord(sp -= 4, ip);                  // STACKFRAME_NEXT_INSTRUCTION_OFFSET
    VM_Magic.setMemoryWord(sp -= 4, INVISIBLE_METHOD_ID); // STACKFRAME_METHOD_ID_OFFSET
    VM_Magic.setMemoryWord(sp -= 4, fp);                  // STACKFRAME_FRAME_POINTER_OFFSET
    fp = sp;

    contextRegisters.gprs[THREAD_ID_REGISTER] = getLockingId();
    contextRegisters.gprs[FRAME_POINTER]  = fp;

//-#endif

    VM.enableGC();

    // only do this at runtime because it will call VM_Magic
    // The first thread which does not have this field initialized will die
    // so it does not need it
    // threadSlot determines which jni function pointer is passed to native C

    if (VM.runningVM)
         jniEnv = new VM_JNIEnvironment(threadSlot);

  }
  
  // Find an empty slot in threads[] array and bind it to this thread.
  // Assumption: call is guarded by threadCreationMutex.
  //
  private void assignThreadSlot() {
    for (int cnt = VM_Scheduler.threads.length; --cnt >= 1; )
       {
       int index = VM_Scheduler.threadAllocationIndex;
       if (++VM_Scheduler.threadAllocationIndex == VM_Scheduler.threads.length)
          VM_Scheduler.threadAllocationIndex = 1;
       if (VM_Scheduler.threads[index] == null)
         {
         //  Problem:
         //  We'd like to say "VM_Scheduler.threads[index] = this;"
         //  but can't do "checkstore" with thread switching disabled, because it will try to acquire
         //  "VM_ClassLoader.lock", so we must hand code the operation via magic.
         //
         threadSlot = index;
         VM_Magic.setObjectAtOffset(VM_Scheduler.threads, threadSlot << 2, this);
	 if (VM.BuildForConcurrentGC && index > maxThreadIndex)
	     maxThreadIndex = index;
         return;
         }
       }
    VM.sysFail("too many threads"); // !!TODO: grow threads[] array
  }

  // Release this thread's threads[] slot.
  // Assumption: call is guarded by threadCreationMutex.
  //
  final void releaseThreadSlot() {
    //  Problem:
    //  We'd like to say "VM_Scheduler.threads[index] = null;"
    //  but can't do "checkstore" inside dispatcher (with thread switching enabled) without
    //  losing control to a threadswitch, so we must hand code the operation via magic.
    //
    VM_Magic.setObjectAtOffset(VM_Scheduler.threads, threadSlot << 2, null);
    VM_Scheduler.threadAllocationIndex = threadSlot;
    if (VM.VerifyAssertions) threadSlot = -1; // ensure trap if we ever try to "become" this thread again
  }

  // Dump this thread, for debugging.
  //
  void
  dump() {
    VM_Scheduler.writeString(" ");
    VM_Scheduler.writeDecimal(getIndex());   // id
    if (isDaemon)      VM_Scheduler.writeString("-daemon");     // daemon thread?
    if (isNativeIdleThread)
      VM_Scheduler.writeString("-nativeidle");    // NativeIdle
    else if (isIdleThread)
      VM_Scheduler.writeString("-idle");       // idle thread?
    if (isGCThread)    VM_Scheduler.writeString("-collector");  // gc thread?
    if (isNativeDaemonThread)  VM_Scheduler.writeString("-nativeDaemon");  // NativeDaemon
    if (beingDispatched)       VM_Scheduler.writeString("-being_dispatched");
  }


   // Needed for support of suspend/resume     CRA:

public boolean is_suspended()
   {
   return isSuspended;
   }

  //-----------------//
  // Instance fields //
  //-----------------//

  // support for suspend and resume
  //
  VM_ProcessorLock suspendLock;
  boolean          suspendPending;
  boolean          suspended;
  
  // Index of this thread in "VM_Scheduler.threads"
  // Value must be non-zero because it is shifted and used in Object lock ownership tests.
  //
  private int threadSlot;
  // Proxywait/wakeup queue object.  
  VM_Proxy proxy;
  
  // Has this thread been suspended via (java/lang/Thread).suspend()
  protected volatile boolean isSuspended; 		// in support of suspend/resume
 
  // Is an exception waiting to be delivered to this thread?
  // A non-null value means next yield() should deliver specified exception to this thread.
  //
  Throwable externalInterrupt; 
  
  // Assertion checking while manipulating raw addresses - see disableGC/enableGC.
  // A value of "true" means it's an error for this thread to call "new".
  //
  boolean disallowAllocationsByThisThread; 

  // Execution stack for this thread.
  //
  int[] stack;      // machine stack on which to execute this thread
  int   stackLimit; // address of stack guard area
  
  // Place to save register state when this thread is not actually running.
  //
  VM_Registers contextRegisters; 
  
  // Place to save register state when C signal handler traps an exception while this thread is running.
  //
  VM_Registers hardwareExceptionRegisters;
  
  // Place to save/restore this thread's monitor state during "wait" and "notify".
  //
  Object waitObject; // object on which this thread is blocked, waiting for a notification
  int    waitCount;  // lock recursion count for this thread's monitor
  
  // If this thread is sleeping, when should it be awakened?
  //
  double wakeupTime;
  
  // Info if this thread is waiting for i/o.
  //
  int     waitFdRead;  // file/socket descriptor that thread is waiting to read
  int     waitFdWrite;  // file/socket descriptor that thread is waiting to read
  boolean waitFdReady; // can operation on file/socket proceed without blocking?
  
  // Scheduling priority for this thread.
  // Note that: java.lang.Thread.MIN_PRIORITY <= priority <= MAX_PRIORITY
  //
  protected int priority;
   
  // Virtual processor that this thread wants to run on (null --> any processor is ok).
  //
  VM_Processor processorAffinity;

  // Virtual Processor to run native methods for this thread
  //
  VM_Processor nativeAffinity;
 
  // Virtual Processor to return from native methods 
  //
  VM_Processor returnAffinity;
 
  // Is this thread's stack being "borrowed" by thread dispatcher (ie. while choosing next thread to run)?
  //
  boolean beingDispatched;

  // This thread's successor on a VM_ThreadQueue.
  //
  VM_Thread next;       
  
  // A thread is "alive" if its start method has been called and the thread has not yet terminated execution.
  // Set by:   java.lang.Thread.start()
  // Unset by: VM_Thread.terminate()
  //
  protected boolean isAlive;

  // A thread is a "gc thread" if it's an instance of VM_CollectorThread
  //
  boolean isGCThread;

  // A thread is an "idle thread" if it's an instance of VM_IdleThread
  //
  boolean isIdleThread;

  // A thread is an "native idle  thread" if it's an instance of VM_NativeIdleThread
  //
  boolean isNativeIdleThread;
  
  // A thread is a "native daemon  thread" if it's an instance of VM_NativedaemonThread
  //
  boolean isNativeDaemonThread;
  
  // The virtual machine terminates when the last non-daemon (user) thread terminates.
  //
  protected boolean isDaemon;
  
  VM_JNIEnvironment  jniEnv;
  
  // fields needed for RCGC reference counting collector
  boolean        stackBufferNeedScan;
  int[]          stackBuffer;        // the buffer
  int[]          stackBufferTop;     // pointer to most recently filled slot in buffer (an address, not an index)
  int[]          stackBufferMax;     // pointer to last available slot in buffer (an address, not an index)
  int            stackBufferCurrent;
  boolean        stackBufferSame;    // are the two stack buffers the same?
  static int     maxThreadIndex = 16;
  
  // Cpu utilization statistics, used if "VM_BuildForCpuMonitoring == true".
  //
  double         cpuStartTime = -1;  // time at which this thread started running on a cpu (-1: has never run, 0: not currently running)
  double         cpuTotalTime;       // total cpu time used by this thread so far, in seconds

  // Network utilization statistics, used if "VM_BuildForNetworkMonitoring == true".
  //
  public int     netReads;           // number of completed read operations
  public int     netWrites;          // number of completed write operations
}
