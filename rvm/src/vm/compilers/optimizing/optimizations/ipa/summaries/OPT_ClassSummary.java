/*
 * (C) Copyright IBM Corp. 2001,2005
 */
//$Id$
package com.ibm.JikesRVM.opt;

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;

/**
 * Hold semantic information about a class that is not defined in
 * {@link VM_Class}.
 * 
 * @author Stephen Fink
 */
public class OPT_ClassSummary {

  /**
   * @param v lightweight class corresponding to this {@link OPT_Class}
   */
  OPT_ClassSummary (VM_Class v) {
    vmClass = v;
  }
  /**
   * class this object tracks
   */
  VM_Class vmClass; 
}

