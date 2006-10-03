/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2002
 */
//$Id$
package org.vmmagic.pragma; 

import com.ibm.JikesRVM.classloader.*;
/**
 * This pragma indicates that a particular method should never be inlined
 * by the optimizing compiler.
 * 
 * @author Stephen Fink
 */
public class NoInlinePragma extends PragmaException {
  private static final VM_TypeReference me = getTypeRef("Lorg/vmmagic/pragma/NoInlinePragma;");

  public static boolean declaredBy(VM_Method method) {
    if (me == null) {

 System.out.println("me is null");
      if(null == getTypeRef("Lorg/vmmagic/pragma/NoInlinePragma;")) 
 System.out.println("And if i get it again it is also null");
    }
    return declaredBy(me, method);
  }
}
