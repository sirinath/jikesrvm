/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2003
 */

package com.ibm.JikesRVM.memoryManagers.JMTk.utility.statistics;

import com.ibm.JikesRVM.memoryManagers.JMTk.Log;

import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
import com.ibm.JikesRVM.VM_Uninterruptible;
import com.ibm.JikesRVM.VM_PragmaInline;

/**
 * This class implements a simple timer.
 *
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 * @version $Revision$
 * @date $Date$
 * $Id$
 */
public class Timer extends LongCounter
  implements VM_Uninterruptible {

  /****************************************************************************
   *
   * Initialization
   */

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   */
  public Timer(String name) {
    this(name, true, false);
  }

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   * @param start True if this counter is to be implicitly started at
   * boot time (otherwise the counter must be explicitly started).
   */
  public Timer(String name, boolean start) {
    this(name, start, false);
  }

  /**
   * Constructor
   *
   * @param name The name to be associated with this counter
   * @param start True if this counter is to be implicitly started at
   * boot time (otherwise the counter must be explicitly started).
   * @param gconly True if this counter only pertains to (and
   * therefore functions during) GC phases.
   */
  public Timer(String name, boolean start, boolean gconly) {
    super(name, start, gconly);
  }

  /****************************************************************************
   *
   * Counter-specific methods
   */

  /**
   * Get the current value for this timer
   *
   * @return The current value for this timer
   */
  final protected long getCurrentValue() throws VM_PragmaInline {
    return VM_Interface.cycles();
  }

  /**
   * Print the total in microseconds
   */
  final void printTotalMicro() {
    printMicro(totalCount);
  }

  /**
   * Print the total in milliseconds
   */
  public final void printTotalMillis() {
    printMillis(totalCount);
  }

  /**
   * Print the total in seconds
   */
  public final void printTotalSecs() {
    printSecs(totalCount);
  }

  /**
   * Print a value (in milliseconds)
   *
   * @param value The value to be printed
   */
  final void printValue(long value) {
    printMillis(value);
  }

  /**
   * Print a value in microseconds
   *
   * @param value The value to be printed
   */
  final void printMicro(long value) {
    Log.write(1000*VM_Interface.cyclesToMillis(value));
  }

  /**
   * Print a value in milliseconds
   *
   * @param value The value to be printed
   */
  final void printMillis(long value) {
    Log.write(VM_Interface.cyclesToMillis(value));
  }

  /**
   * Print a value in seconds
   *
   * @param value The value to be printed
   */
  final void printSecs(long value) {
    Log.write(VM_Interface.cyclesToSecs(value));
  }
}

