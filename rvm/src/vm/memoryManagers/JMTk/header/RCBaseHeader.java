/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

package com.ibm.JikesRVM.memoryManagers.JMTk;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.memoryManagers.vmInterface.Constants;

import com.ibm.JikesRVM.VM_Address;
import com.ibm.JikesRVM.VM_Magic;

import com.ibm.JikesRVM.VM_PragmaInline;
import com.ibm.JikesRVM.VM_PragmaNoInline;
import com.ibm.JikesRVM.VM_PragmaUninterruptible;
import com.ibm.JikesRVM.VM_PragmaLogicallyUninterruptible;

/**
 * Defines header words used by memory manager.not used for 
 *
 * @see VM_ObjectModel
 * 
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 */
public abstract class RCBaseHeader implements Constants {

  /**
   * How many bytes are used by all GC header fields?
   */
  public static final int NUM_BYTES_HEADER = BYTES_IN_ADDRESS;
  protected static final int RC_HEADER_OFFSET = VM_Interface.GC_HEADER_OFFSET();

  /**
   * How many bits does this GC system require?
   */
  public static final int REQUESTED_BITS    = 2;
  static final int GC_BITS_MASK      = 0x3;

  static final int      BARRIER_BIT = 1;
  static final int BARRIER_BIT_MASK  = 1<<BARRIER_BIT;  // ...10

  static final int DEC_KILL = 0;    // dec to zero RC --> reclaim obj
  static final int DEC_PURPLE = 1;  // dec to non-zero RC, already buf'd
  static final int DEC_BUFFER = -1; // dec to non-zero RC, need to bufr

  /**
   * Perform any required initialization of the GC portion of the header.
   * Called for objects allocated at boot time.
   * 
   * @param ref the object ref to the storage to be initialized
   * @param tib the TIB of the instance being created
   * @param size the number of bytes allocated by the GC system for this object.
   * @param isScalar are we initializing a scalar (true) or array (false) object?
   */
  public static void initializeHeaderBootTime(int ref, Object[] tib, 
	                                      int size, boolean isScalar)
    throws VM_PragmaUninterruptible {
    // nothing to do for boot image objects
  }

  /**
   * For low level debugging of GC subsystem. 
   * Dump the header word(s) of the given object reference.
   * @param ref the object reference whose header should be dumped 
   */
  public static void dumpHeader(VM_Address ref) 
    throws VM_PragmaUninterruptible {
    // nothing to do (no bytes of GC header)
  }


  /****************************************************************************
   * 
   * Core ref counting support
   */

  /**
   * Return true if given object is live
   *
   * @param object The object whose liveness is to be tested
   * @return True if the object is alive
   */
  static boolean isLiveRC(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) >= LIVE_THRESHOLD;
  }

  static void incRCOOL(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaNoInline {
    incRC(object);
  }
  /**
   * Increment the reference count of an object, clearing the "purple"
   * status of the object (if it were already purple).  An object is
   * marked purple if it is a potential root of a garbage cycle.  If
   * an object's RC is incremented, it must be live and therefore
   * should not be considered as a potential garbage cycle.  This must
   * be an atomic operation if parallel GC is supported.
   *
   * @param object The object whose RC is to be incremented.
   */
  static void incRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    //    Log.write("si[");Log.write(object);RCBaseHeader.print(object);
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = oldValue + INCREMENT;
      if (Plan.REF_COUNT_CYCLE_DETECTION) newValue = (newValue & ~PURPLE);
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
    //    Log.write(' ');RCBaseHeader.print(object);Log.writeln("]");
  }

  /**
   * Decrement the reference count of an object.  Return either
   * <code>DEC_KILL</code> if the count went to zero,
   * <code>DEC_BUFFER</code> if the count did not go to zero and the
   * object was not already in the purple buffer, and
   * <code>DEC_PURPLE</code> if the count did not go to zero and the
   * object was already in the purple buffer.  This must be an atomic
   * operation if parallel GC is supported.
   *
   * @param object The object whose RC is to be decremented.
   * @return <code>DEC_KILL</code> if the count went to zero,
   * <code>DEC_BUFFER</code> if the count did not go to zero and the
   * object was not already in the purple buffer, and
   * <code>DEC_PURPLE</code> if the count did not go to zero and the
   * object was already in the purple buffer.
   */
  static int decRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    int rtn;
    //    Log.write("sd[");Log.write(object);RCBaseHeader.print(object);
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = oldValue - INCREMENT;
      if (newValue < LIVE_THRESHOLD)
	rtn = DEC_KILL;
      else if (Plan.REF_COUNT_CYCLE_DETECTION && 
	       ((newValue & COLOR_MASK) < PURPLE)) { // if not purple or green
	rtn = ((newValue & BUFFERED_MASK) == 0) ? DEC_BUFFER : DEC_PURPLE;
	newValue = (newValue & ~COLOR_MASK) | PURPLE | BUFFERED_MASK;
      } else
	rtn = DEC_PURPLE;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
    //    Log.write(' ');RCBaseHeader.print(object);Log.writeln("]");
    return rtn;
  }
  
  static boolean isBuffered(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getRCbits(object, BUFFERED_MASK) != 0;
  }

  /****************************************************************************
   * 
   * Coalescing support
   */

  static boolean attemptBarrierBitSet(VM_Address ref)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int old = VM_Interface.readAvailableBitsWord(ref);
    boolean rtn = ((old & BARRIER_BIT_MASK) == 0);
    if (rtn) {
      do {
	old = VM_Interface.prepareAvailableBits(ref);
	rtn = ((old & BARRIER_BIT_MASK) == 0);
      } while(!VM_Interface.attemptAvailableBits(ref, old, 
						 old | BARRIER_BIT_MASK)
	      && rtn);
    }
    return rtn;
  }

  static void clearBarrierBit(VM_Address ref) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    VM_Interface.setAvailableBit(ref, BARRIER_BIT, false);
  }

  private static int getRCbits(VM_Address object, int mask)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) & mask;
  }

  /**
   * Set the <code>ROOT_REACHABLE</code> bit for an object if it is
   * not already set.  Return true if it was not already set, false
   * otherwise.
   *
   * @param object The object whose <code>ROOT_REACHABLE</code> bit is
   * to be set.
   * @return <code>true</code> if it was set by this call,
   * <code>false</code> if the bit was already set.
   */
  static boolean setRoot(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      if ((oldValue & ROOT_REACHABLE) == ROOT_REACHABLE)
	return false;
      newValue = oldValue | ROOT_REACHABLE;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
    return true;
  }

  /**
   * Clear the <code>ROOT_REACHABLE</code> bit for an object.
   *
   * @param object The object whose <code>ROOT_REACHABLE</code> bit is
   * to be cleared.
   */
  static void unsetRoot(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    do {
      oldValue = VM_Magic.prepareInt(object, RC_HEADER_OFFSET);
      newValue = oldValue & ~ROOT_REACHABLE;
    } while (!VM_Magic.attemptInt(object, RC_HEADER_OFFSET, oldValue, newValue));
  }

  /****************************************************************************
   * 
   * Trial deletion support
   */

  /**
   * Decrement the reference count of an object. This is unsychronized.
   *
   * @param object The object whose RC is to be decremented.
   */
  static void unsyncDecRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    int rtn;
    oldValue = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
    newValue = oldValue - INCREMENT;
    //    Log.write("ud[");Log.write(object);RCBaseHeader.print(object);
    VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, newValue);
    //    Log.write(' ');RCBaseHeader.print(object);Log.writeln("]");
  }

  /**
   * Increment the reference count of an object. This is unsychronized.
   *
   * @param object The object whose RC is to be incremented.
   */
  static void unsyncIncRC(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue, newValue;
    oldValue = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
    newValue = oldValue + INCREMENT;
    //    Log.write("ui[");Log.write(object);RCBaseHeader.print(object);
    VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, newValue);
    //    Log.write(' ');RCBaseHeader.print(object);Log.writeln("]");
  }

  static void print(VM_Address object)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    Log.write(' ');
    Log.write(VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET)>>INCREMENT_SHIFT); 
    Log.write(' ');
    switch (getHiRCColor(object)) {
    case PURPLE: Log.write('p'); break;
    case GREEN: Log.write('g'); break;
    }
    switch (getLoRCColor(object)) {
    case BLACK: Log.write('b'); break;
    case WHITE: Log.write('w'); break;
    case GREY: Log.write('g'); break;
    }
    if (isBuffered(object))
      Log.write('b');
    else
      Log.write('u');
  }
  static void clearBufferedBit(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
    int newValue = oldValue & ~BUFFERED_MASK;
    VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, newValue);
  }
  static boolean isBlack(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getLoRCColor(object) == BLACK;
  }
  static boolean isWhite(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getLoRCColor(object) == WHITE;
  }
  static boolean isGreen(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getHiRCColor(object) == GREEN;
  }
  static boolean isPurple(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getHiRCColor(object) == PURPLE;
  }
  static boolean isPurpleNotGrey(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return (VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET) & (PURPLE | GREY)) == PURPLE;
  }
  static boolean isGrey(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return getLoRCColor(object) == GREY;
  }
  private static int getRCColor(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return COLOR_MASK & VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
  }
  private static int getLoRCColor(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return LO_COLOR_MASK & VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
  }
  private static int getHiRCColor(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    return HI_COLOR_MASK & VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
  }
  static void makeBlack(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    changeRCLoColor(object, BLACK);
  }
  static void makeWhite(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    changeRCLoColor(object, WHITE);
  }
  static void makeGrey(VM_Address object) 
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    if (VM_Interface.VerifyAssertions) 
      VM_Interface._assert(getHiRCColor(object) != GREEN);
    changeRCLoColor(object, GREY);
  }
  private static void changeRCLoColor(VM_Address object, int color)
    throws VM_PragmaUninterruptible, VM_PragmaInline {
    int oldValue = VM_Magic.getIntAtOffset(object, RC_HEADER_OFFSET);
    int newValue = (oldValue & ~LO_COLOR_MASK) | color;
    VM_Magic.setIntAtOffset(object, RC_HEADER_OFFSET, newValue);
  }

  // See Bacon & Rajan ECOOP 2001 for notion of colors (purple, grey,
  // black, green).  See also Jones & Lins for a description of "Lins'
  // algorithm", on which Bacon & Rajan's is based.

  // The following are arranged to try to make the most common tests
  // fastest ("bufferd?", "green?" and "(green | purple)?") 
  private static final int     BUFFERED_MASK = 0x1;  //  .. xx0001
  private static final int        COLOR_MASK = 0x1e;  //  .. x11110 
  private static final int     LO_COLOR_MASK = 0x6;  //  .. x00110 
  private static final int     HI_COLOR_MASK = 0x18; //  .. x11000 
  private static final int             BLACK = 0x0;  //  .. xxxx0x
  private static final int              GREY = 0x2;  //  .. xxxx1x
  private static final int             WHITE = 0x4;  //  .. xx010x
  // green & purple *MUST* remain the highest colors in order to
  // preserve the (green | purple) test's precondition.
  private static final int            PURPLE = 0x8;  //  .. x01xxx
  protected static final int           GREEN = 0x10;  // .. x10xxx

  // bits used to ensure retention of objects with zero RC
  private static final int    ROOT_REACHABLE = 0x20; //  .. x10000
  private static final int       FINALIZABLE = 0x40; //  .. 100000
  private static final int    LIVE_THRESHOLD = ROOT_REACHABLE;
  private static final int         BITS_USED = 7;

  protected static final int INCREMENT_SHIFT = BITS_USED;
  protected static final int INCREMENT = 1<<INCREMENT_SHIFT;
  protected static final int AVAILABLE_BITS = BITS_IN_ADDRESS - BITS_USED;
  protected static final int INCREMENT_BITS = AVAILABLE_BITS;
  protected static final int INCREMENT_MASK = ((1<<INCREMENT_BITS)-1)<<INCREMENT_SHIFT;
}
