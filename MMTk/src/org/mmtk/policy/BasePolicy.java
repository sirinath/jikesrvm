/*
 * (C) Copyright IBM Corp. 2001
 * (C) Copyright Department of Computer Science,
 *     Australian National University. 2002
 */

package com.ibm.JikesRVM.memoryManagers.JMTk;


import com.ibm.JikesRVM.VM_Address;

/**
 * @author Perry Cheng
 * @author <a href="http://cs.anu.edu.au/~Steve.Blackburn">Steve Blackburn</a>
 */
import com.ibm.JikesRVM.memoryManagers.vmInterface.VM_Interface;
abstract public class BasePolicy { // implements HeaderConstants {
  
  public final static String Id = "$Id$"; 

  public static void prepare(VMResource vm, MemoryResource mr) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false);
  }
  public static void release(VMResource vm, MemoryResource mr) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false); 
  }
  public static VM_Address traceObject(VM_Address object) { 
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false); 
    return VM_Address.zero(); 
  }
  public static    boolean isLive(VM_Address obj) {
    if (VM_Interface.VerifyAssertions) VM_Interface._assert(false); 
    return false; 
  }
}
