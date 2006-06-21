/*
 * (C) Copyright Department of Computer Science,
 * Australian National University. 2004
 *
 * (C) Copyright IBM Corp. 2001, 2003
 */
package org.mmtk.vm;

/**
 * $Id$
 * 
 * @author Steve Blackburn
 * @author Perry Cheng
 * 
 * @version $Revision$
 * @date $Date$
 */
public class Assert {
  /**
   * <code>true</code> if assertions should be verified
   */
  public static final boolean VERIFY_ASSERTIONS = false;

  /**
   * This method should be called whenever an error is encountered.
   * 
   * @param str
   *          A string describing the error condition.
   */
  public static void error(String str) {
  }

  /**
   * Logs a message and traceback, then exits.
   * 
   * @param message
   *          the string to log
   */
  public static void fail(String message) {
  }

  /**
   * Exit the virtual machine.
   * 
   * @param rc
   *          Return code to exit with.
   */
  public static void exit(int rc) {
  }

  /**
   * Checks that the given condition is true. If it is not, this method does a
   * traceback and exits.
   * 
   * @param cond
   *          the condition to be checked
   */
  public static void _assert(boolean cond) {
  }

  /**
   * Checks that the given condition is true. If it is not, this method prints a
   * message, does a traceback and exits.
   * 
   * @param cond
   *          the condition to be checked
   * @param message
   *          the message to print
   */
  public static void _assert(boolean cond, String message) {
  }

  /**
   * Print a stack trace
   */
  public static final void dumpStack() {
  }

  /**
   * Throw an out of memory exception.
   */
  public static void failWithOutOfMemoryError() {
  }

  /**
   * Checks if the virtual machine is running. This value changes, so the
   * call-through to the VM must be a method. In Jikes RVM, just returns
   * VM.runningVM.
   * 
   * @return <code>true</code> if the virtual machine is running
   */
  public static boolean runningVM() {
    return false;
  }

}
