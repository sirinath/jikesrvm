/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Structure for the D and XL forms, PowerPC instruction set 
 *
 * @author John Waley
 * @see PPC_Disassembler 
 */
class opcode_tab {  

  int format;
  String mnemonic;

  opcode_tab(int format, String mnemonic) {
    this.format = format;
    this.mnemonic = mnemonic;
  }

}
