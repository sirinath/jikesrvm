/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2002
 */
package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.AllocAdvice;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Type;
import com.ibm.JikesRVM.memoryManagers.vmInterface.CallSite;

import com.ibm.JikesRVM.VM;
import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Extent;
import com.ibm.JikesRVM.VM_Magic;
import com.ibm.JikesRVM.VM_ObjectModel;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_Scheduler;
import com.ibm.JikesRVM.classloader.VM_Array;

/**
 * This class implements a simple allocator without a collector.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 */
public class Plan extends StopTheWorldGC implements VM_Uninterruptible {
  public static final String Id = "$Id$"; 


  ////////////////////////////////////////////////////////////////////////////
  //
  // Class variables
  //
  public static final boolean needsWriteBarrier = false;
  public static final boolean needsRefCountWriteBarrier = false;
  public static final boolean refCountCycleDetection = false;
  public static final boolean movesObjects = false;

  // virtual memory resources
  private static MonotoneVMResource defaultVM;

  // memory resources
  private static MemoryResource defaultMR;

  // Allocators
  public static final byte DEFAULT_SPACE = 0;

  // Miscellaneous constants
  private static final int POLL_FREQUENCY = DEFAULT_POLL_FREQUENCY;
  
  // Memory layout constants
  public  static final long            AVAILABLE = VM_Interface.MAXIMUM_MAPPABLE.diff(PLAN_START).toLong();
  private static final VM_Extent    DEFAULT_SIZE = Conversions.roundDownMB(VM_Extent.fromInt((int)(0.5 * AVAILABLE)));
  public  static final VM_Extent        MAX_SIZE = DEFAULT_SIZE;

  private static final VM_Address  DEFAULT_START = PLAN_START;
  private static final VM_Address    DEFAULT_END = DEFAULT_START.add(DEFAULT_SIZE);
  private static final VM_Address       HEAP_END = DEFAULT_END;


  ////////////////////////////////////////////////////////////////////////////
  //
  // Instance variables
  //

  // allocators
  private BumpPointer def;

  ////////////////////////////////////////////////////////////////////////////
  //
  // Initialization
  //

  /**
   * Class initializer.  This is executed <i>prior</i> to bootstrap
   * (i.e. at "build" time).  This is where key <i>global</i>
   * instances are allocated.  These instances will be incorporated
   * into the boot image by the build process.
   */
  static {
    defaultMR = new MemoryResource("def", POLL_FREQUENCY);
    defaultVM = new MonotoneVMResource(DEFAULT_SPACE, "default", defaultMR, DEFAULT_START, DEFAULT_SIZE, VMResource.IN_VM);

    addSpace(DEFAULT_SPACE, "Default Space");
  }

  /**
   * Constructor
   */
  public Plan() {
    def = new BumpPointer(defaultVM);
  }

  /**
   * The boot method is called early in the boot process before any
   * allocation.
   */
  public static final void boot()
    throws VM_PragmaInterruptible {
    StopTheWorldGC.boot();
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Allocation
  //

  /**
   * Allocate space (for an object)
   *
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @param allocator The allocator number to be used for this allocation
   * @param advice Statically-generated allocation advice for this allocation
   * @return The address of the first byte of the allocated region
   */
  public final VM_Address alloc (EXTENT bytes, boolean isScalar, int allocator,
				AllocAdvice advice)
    throws VM_PragmaInline {
    switch (allocator) {
      case  DEFAULT_SPACE:  return def.alloc(isScalar, bytes);
      case IMMORTAL_SPACE:  return immortal.alloc(isScalar, bytes);
      default:              if (VM.VerifyAssertions) VM.sysFail("No such allocator"); 
	                    return VM_Address.zero();
    }
  }

  /**
   * Perform post-allocation actions.  For many allocators none are
   * required.
   *
   * @param ref The newly allocated object
   * @param tib The TIB of the newly allocated object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   * @param allocator The allocator number to be used for this allocation
   */
  public final void postAlloc(Object ref, Object[] tib, EXTENT bytes,
			      boolean isScalar, int allocator)
    throws VM_PragmaInline {
    switch (allocator) {
      case  DEFAULT_SPACE: return;
      case IMMORTAL_SPACE: Immortal.postAlloc(ref); return;
      default:             if (VM.VerifyAssertions) VM.sysFail("No such allocator");
    }
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
				    boolean isScalar) 
    throws VM_PragmaInline {
    VM.sysFail("no allocCopy in noGC");
    return VM_Address.zero();
  }

  /**  
   * Perform any post-copy actions.  In this case nothing is required.
   *
   * @param ref The newly allocated object
   * @param tib The TIB of the newly allocated object
   * @param bytes The size of the space to be allocated (in bytes)
   * @param isScalar True if the object occupying this space will be a scalar
   */
  public final void postCopy(Object ref, Object[] tib, EXTENT bytes,
			     boolean isScalar) {
    VM.sysFail("no postCopy in noGC");
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
    return DEFAULT_SPACE;
  }

  protected final byte getSpaceFromAllocator (Allocator a) {
    if (a == def) return DEFAULT_SPACE;
    return super.getSpaceFromAllocator(a);
  }

  protected final Allocator getAllocatorFromSpace (byte s) {
    if (s == DEFAULT_SPACE) return def;
    return super.getAllocatorFromSpace(s);
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
   * Return the initial header value for a newly allocated LOS
   * instance.
   *
   * @param bytes The size of the newly created instance in bytes.
   * @return The inital header value for the new instance.
   */
  public static final int getInitialHeaderValue(EXTENT bytes)
    throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(false);
    return 0;
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
   * <code>collect()</code> method of this class or its superclass.<p>
   *
   * This method is clearly interruptible since it can lead to a GC.
   * However, the caller is typically uninterruptible and this fiat allows 
   * the interruptibility check to work.  The caveat is that the caller 
   * of this method must code as though the method is interruptible. 
   * In practice, this means that, after this call, processor-specific
   * values must be reloaded.
   *
   * @param mustCollect True if a this collection is forced.
   * @param mr The memory resource that triggered this collection.
   * @return True if a collection is triggered
   */
  public final boolean poll(boolean mustCollect, MemoryResource mr) 
    throws VM_PragmaLogicallyUninterruptible {
    if (getPagesReserved() > getTotalPages())
      error("Out of memory");
    return false;
  }

  /**
   * Perform operations with <i>global</i> scope in preparation for a
   * collection.  This is called by <code>StopTheWorld</code>, which will
   * ensure that <i>only one thread</i> executes this.<p>
   *
   * In this case, it means flipping semi-spaces, resetting the
   * semi-space memory resource, and preparing each of the collectors.
   */
  protected final void globalPrepare() {
    if (VM.VerifyAssertions) VM._assert(false);
  }

  /**
   * Perform operations with <i>thread-local</i> scope in preparation
   * for a collection.  This is called by <code>StopTheWorld</code>, which
   * will ensure that <i>all threads</i> execute this.<p>
   *
   * In this case, it means resetting the semi-space and large object
   * space allocators.
   */
  protected final void threadLocalPrepare(int count) {
    if (VM.VerifyAssertions) VM._assert(false);
  }

  /**
   * We reset the state for a GC thread that is not participating in
   * this GC
   */
  public final void prepareNonParticipating() {
    if (VM.VerifyAssertions) VM._assert(false);
  }

  /**
   * Perform operations with <i>thread-local</i> scope to clean up at
   * the end of a collection.  This is called by
   * <code>StopTheWorld</code>, which will ensure that <i>all threads</i>
   * execute this.<p>
   *
   * In this case, it means releasing the large object space (which
   * triggers the sweep phase of the mark-sweep collector used by the
   * LOS).
   */
  protected final void threadLocalRelease(int count) {
    if (VM.VerifyAssertions) VM._assert(false);
  }

  /**
   * Perform operations with <i>global</i> scope to clean up at the
   * end of a collection.  This is called by <code>StopTheWorld</code>,
   * which will ensure that <i>only one</i> thread executes this.<p>
   *
   * In this case, it means releasing each of the spaces and checking
   * whether the GC made progress.
   */
  protected final void globalRelease() {
    if (VM.VerifyAssertions) VM._assert(false);
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Object processing and tracing
  //

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i> an
   * interior pointer.
   * @return The possibly moved reference.
   */
  public static final VM_Address traceObject (VM_Address obj) throws VM_PragmaInline {
    if (VM.VerifyAssertions) VM._assert(false);
    return VM_Address.zero();
  }

  /**
   * Trace a reference during GC.  This involves determining which
   * collection policy applies and calling the appropriate
   * <code>trace</code> method.
   *
   * @param obj The object reference to be traced.  This is <i>NOT</i>
   * an interior pointer.
   * @param root True if this reference to <code>obj</code> was held
   * in a root.
   * @return The possibly moved reference.
   */
  public static final VM_Address traceObject(VM_Address obj, boolean root) {
    if (VM.VerifyAssertions) VM._assert(false);
    return VM_Address.zero();
  }


  /**
   * Return true if <code>obj</code> is a live object.
   *
   * @param obj The object in question
   * @return True if <code>obj</code> is a live object.
   */
  public static final boolean isLive(VM_Address obj) {
    if (obj.isZero()) return false;
    VM_Address addr = VM_Interface.refToAddress(obj);
    byte space = VMResource.getSpace(addr);
    switch (space) {
      case DEFAULT_SPACE:   return true;
      case IMMORTAL_SPACE:  return true;
      case BOOT_SPACE:	    return true;
      case META_SPACE:	    return true;
      default:              if (VM.VerifyAssertions) {
	                      VM.sysWriteln("Plan.isLive: unknown space", space);
			      VM.sysFail("Plan.isLive: unknown space");
                            }
			    return false;
    }
  }


  public static boolean willNotMove (VM_Address obj) {
    return true;
  }


  public static final int resetGCBitsForCopy(VM_Address fromObj,
					     int forwardingWord, int bytes) {
    if (VM.VerifyAssertions) VM._assert(false);
    return forwardingWord;
  }

  ////////////////////////////////////////////////////////////////////////////
  //
  // Space management
  //

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  This <i>includes</i> space reserved for copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, including space reserved for copying.
   */
  protected static final int getPagesReserved() {
    int pages = defaultMR.reservedPages();
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }

  /**
   * Return the number of pages reserved for use given the pending
   * allocation.  This is <i>exclusive of</i> space reserved for
   * copying.
   *
   * @return The number of pages reserved given the pending
   * allocation, excluding space reserved for copying.
   */
  protected static final int getPagesUsed() {
    int pages = defaultMR.reservedPages();
    pages += immortalMR.reservedPages();
    pages += metaDataMR.reservedPages();
    return pages;
  }

  /**
   * Return the number of pages available for allocation, <i>assuming
   * all future allocation is to the semi-space</i>.
   *
   * @return The number of pages available for allocation, <i>assuming
   * all future allocation is to the semi-space</i>.
   */
  protected static final int getPagesAvail() {
    return (getTotalPages() - defaultMR.reservedPages() 
	    - immortalMR.reservedPages());
  }


  ////////////////////////////////////////////////////////////////////////////
  //
  // Miscellaneous
  //

  /**
   * Show the status of each of the allocators.
   */
  public final void show() {
    def.show();
    immortal.show();
  }


}
