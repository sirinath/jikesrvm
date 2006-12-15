/*
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
package com.ibm.jikesrvm.mm.mmtk;

import org.mmtk.plan.TraceLocal;
import org.mmtk.utility.Constants;
import com.ibm.jikesrvm.VM_Statics;
import com.ibm.jikesrvm.VM_Magic;
import com.ibm.jikesrvm.VM_Thread;
import com.ibm.jikesrvm.memorymanagers.mminterface.VM_CollectorThread;

import org.vmmagic.unboxed.*;
import org.vmmagic.pragma.*;

/**
 * Class that determines all JTOC slots (statics) that hold references
 *
 * $Id$
 *
 * @author Perry Cheng
 * @author Ian Rogers
 */  
public final class ScanStatics implements Constants {
  /**
   * Size in 32bits words of a JTOC slot (ie 32bit addresses = 1,
   * 64bit addresses =2)
   */
  private final static int refSlotSize = VM_Statics.getReferenceSlotSize();
  /**
   * Mask used when calculating the chunkSize to ensure chunks are
   * 64bit aligned on 64bit architectures
   */
  private final static int chunkSizeMask = 0xFFFFFFFF - (refSlotSize - 1);
  /**
   * Scan static variables (JTOC) for object references.  Executed by
   * all GC threads in parallel, with each doing a portion of the
   * JTOC.
   */
  @Inline
  @Uninterruptible
  public static void scanStatics(TraceLocal trace) { 
    // The address of the statics table
    // equivalent to VM_Statics.getSlots()
    final Address slots = VM_Magic.getJTOC();
    // The number of collector threads
    final int numberOfCollectors = VM_CollectorThread.numCollectors();
    // This thread as a collector
    final VM_CollectorThread ct = VM_Magic.threadAsCollectorThread(VM_Thread.getCurrentThread());
    // The number of static references
    final int numberOfReferences = VM_Statics.getNumberOfReferenceSlots();
    // The size to give each thread
    final int chunkSize = (numberOfReferences / numberOfCollectors) & chunkSizeMask;
    // The number of this collector thread (1...n)
    final int threadOrdinal = ct.getGCOrdinal();

    // Start and end of statics region to be processed
    final int start = (threadOrdinal == 1) ? refSlotSize : (threadOrdinal - 1) * chunkSize;
    final int end = (threadOrdinal == numberOfCollectors) ? numberOfReferences : threadOrdinal * chunkSize;

    // Process region
    for (int slot=start; slot < end; slot+=refSlotSize) {
      Offset slotOffset = Offset.fromIntSignExtend(slot << LOG_BYTES_IN_INT);
      trace.addRootLocation(slots.plus(slotOffset));
    }
  }
}
