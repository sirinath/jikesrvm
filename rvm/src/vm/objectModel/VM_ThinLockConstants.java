/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Constants used to implement thin locks.
 * A portion of a word, either in the object header 
 * or in some other location, is used to provide light weight
 * synchronization operations. This class defines 
 * how the bits available for thin locks are allocated.
 * Either a lock is in fat state, in which case it looks like
 * 1Z..Z where Z..Z is the id of a heavy lock, or it is in
 * thin state in which case it looks like 0I..IC..C where
 * I is the thread id of the thread that owns the lock and
 * C is the recursion count of the lock.
 * <pre>
 * aaaaTTTTTTTTTTbbbbb
 * VM_JavaHeader.NUM_THIN_LOCK_BITS = # of T's
 * VM_JavaHeader.THIN_LOCK_SHIFT = # of b's
 * </pre>
 * 
 * @author Bowen Alpern
 * @author David Bacon
 * @author Stephen Fink
 * @author Dave Grove
 * @author Derek Lieber
 */
public interface VM_ThinLockConstants  extends VM_SizeConstants {

  static final int NUM_BITS_TID        = VM_Scheduler.LOG_MAX_THREADS;
  static final int NUM_BITS_RC         = VM_JavaHeader.NUM_THIN_LOCK_BITS - NUM_BITS_TID;

  static final int TL_LOCK_COUNT_SHIFT = VM_JavaHeader.THIN_LOCK_SHIFT;
  static final int TL_THREAD_ID_SHIFT  = TL_LOCK_COUNT_SHIFT + NUM_BITS_RC;
  static final int TL_LOCK_ID_SHIFT    = VM_JavaHeader.THIN_LOCK_SHIFT;

  static final int TL_LOCK_COUNT_UNIT  = 1 << TL_LOCK_COUNT_SHIFT;

  static final int TL_LOCK_COUNT_MASK  = (-1 >>> (BITS_IN_INT - NUM_BITS_RC))  << TL_LOCK_COUNT_SHIFT;
  static final int TL_THREAD_ID_MASK   = (-1 >>> (BITS_IN_INT - NUM_BITS_TID)) << TL_THREAD_ID_SHIFT;
  static final int TL_LOCK_ID_MASK     = (-1 >>> (BITS_IN_INT - (NUM_BITS_RC + NUM_BITS_TID - 1))) << TL_LOCK_ID_SHIFT;
  static final int TL_FAT_LOCK_MASK    = 1 << (VM_JavaHeader.THIN_LOCK_SHIFT + NUM_BITS_RC + NUM_BITS_TID -1);
  static final int TL_UNLOCK_MASK      = ~((-1 >>> (BITS_IN_INT - VM_JavaHeader.NUM_THIN_LOCK_BITS)) << VM_JavaHeader.THIN_LOCK_SHIFT);
}

