/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Register Usage Conventions for PowerPC.
 *
 * @author Bowen Alpern
 * @author Dave Grove
 * @author Derek Lieber
 */
public interface VM_RegisterConstants extends VM_SizeConstants {
  // Machine instructions.
  //
  static final int LG_INSTRUCTION_WIDTH = 2;                      // log2 of instruction width in bytes, powerPC
  static final int INSTRUCTION_WIDTH = 1 << LG_INSTRUCTION_WIDTH; // instruction width in bytes, powerPC

  // Jikes RVM's general purpose register usage (32 or 64 bits wide based on VM.BuildFor64Addr).
  //
  static final int REGISTER_ZERO              =  0; // special instruction semantics on this register
  static final int FRAME_POINTER              =  1; // AIX is 1
  static final int JTOC_POINTER               =  2; // AIX is 2
  static final int FIRST_VOLATILE_GPR         =  3; // AIX is 3
  //                                            ...
  static final int LAST_VOLATILE_GPR          = 10; // AIX is 10
  static final int FIRST_SCRATCH_GPR          = 11; // AIX is 11
  static final int LAST_SCRATCH_GPR           = 12; // AIX is 12
  // AIX 64 bit ABI reserves R13 for use by libpthread; therefore Jikes RVM doesn't touch it.
  static final int FIRST_RVM_RESERVED_NV_GPR  = VM.BuildFor64Addr? 14 : 13;
  static final int PROCESSOR_REGISTER         = FIRST_RVM_RESERVED_NV_GPR;
  static final int KLUDGE_TI_REG              = PROCESSOR_REGISTER + 1; // migration aid while killing TI
  static final int LAST_RVM_RESERVED_NV_GPR   = 16; // migration kludge; should be KLUDGE_TI_REG
  static final int FIRST_NONVOLATILE_GPR      = LAST_RVM_RESERVED_NV_GPR+1; // AIX is 14
  //                                            ...
  static final int LAST_NONVOLATILE_GPR       = 31; // AIX is 31
  static final int NUM_GPRS                   = 32;

  // Floating point register usage. (FPR's are 64 bits wide).
  //
  static final int FIRST_SCRATCH_FPR          =  0; // AIX is 0
  static final int LAST_SCRATCH_FPR           =  0; // AIX is 0
  static final int FIRST_VOLATILE_FPR         =  1; // AIX is 1
  //                                            ...
  static final int LAST_VOLATILE_FPR          = 13; // AIX is 13
  static final int FIRST_NONVOLATILE_FPR      = 14; // AIX is 14
  //                                            ...
  static final int LAST_NONVOLATILE_FPR       = 31; // AIX is 31
  static final int NUM_FPRS                   = 32;

  static final int NUM_NONVOLATILE_GPRS = LAST_NONVOLATILE_GPR - FIRST_NONVOLATILE_GPR + 1;
  static final int NUM_NONVOLATILE_FPRS = LAST_NONVOLATILE_FPR - FIRST_NONVOLATILE_FPR + 1;

  // condition registers
  // TODO: fill table
  static final int NUM_CRS                    = 8;
   
  // special registers (user visible)
  static final int NUM_SPECIALS               = 8;

  // AIX register convention (for mapping parameters in JNI calls)
  //-#if RVM_FOR_AIX
  static final int FIRST_OS_PARAMETER_GPR         =  3; 
  static final int LAST_OS_PARAMETER_GPR          = 10; // this is really the last parameter passing register
  static final int FIRST_OS_VOLATILE_GPR          =  3;
  static final int LAST_OS_VOLATILE_GPR           = 12;
  static final int FIRST_OS_NONVOLATILE_GPR       = 13;
  static final int FIRST_OS_PARAMETER_FPR         =  1;
  static final int LAST_OS_PARAMETER_FPR          = 13;
  static final int FIRST_OS_VOLATILE_FPR          =  1;
  static final int LAST_OS_VOLATILE_FPR           = 13;
  static final int FIRST_OS_NONVOLATILE_FPR       = 14; 
  static final int LAST_OS_VARARG_PARAMETER_FPR   =  6;
  static final int NATIVE_FRAME_HEADER_SIZE       =  6*BYTES_IN_ADDRESS; // fp + cr + lr + res + res + toc
  //-#endif
  //-#if RVM_FOR_LINUX
  static final int FIRST_OS_PARAMETER_GPR         =  3;
  static final int LAST_OS_PARAMETER_GPR          = 10;
  static final int FIRST_OS_VOLATILE_GPR          =  3;
  static final int LAST_OS_VOLATILE_GPR           = 12;
  static final int FIRST_OS_NONVOLATILE_GPR       = 13;
  static final int FIRST_OS_PARAMETER_FPR         =  1;
  static final int LAST_OS_PARAMETER_FPR          =  8;
  static final int FIRST_OS_NONVOLATILE_FPR       = 14;
  static final int LAST_OS_VARARG_PARAMETER_FPR   =  8;
  // native frame header size, used for java-to-native glue frame header 
  static final int NATIVE_FRAME_HEADER_SIZE       =  2*BYTES_IN_ADDRESS;  // fp + lr
  //-#endif
  //-#if RVM_FOR_OSX
  static final int FIRST_OS_PARAMETER_GPR         =  3;
  static final int LAST_OS_PARAMETER_GPR          = 10;
  static final int FIRST_OS_VOLATILE_GPR          =  3;
  static final int LAST_OS_VOLATILE_GPR           = 12;
  static final int FIRST_OS_NONVOLATILE_GPR       = 13;
  static final int FIRST_OS_PARAMETER_FPR         =  1;
  static final int LAST_OS_PARAMETER_FPR          = 13;
  static final int FIRST_OS_NONVOLATILE_FPR       = 14;
  static final int LAST_OS_VARARG_PARAMETER_FPR   =  8;
  // native frame header size, used for java-to-native glue frame header 
  static final int NATIVE_FRAME_HEADER_SIZE       =  6*BYTES_IN_ADDRESS;  // fp + cp + lr???
  //-#endif

  // Register mnemonics (for use by debugger/machine code printers).
  //
  static final String [] GPR_NAMES = RegisterConstantsHelper.gprNames();
  
  static final String [] FPR_NAMES = RegisterConstantsHelper.fprNames();
}

/**
 * This class exists only to kludge around the fact that we can't
 * put static clinit blocks in interfaces.  As a result,
 * it is awkward to write 'nice' code to initialize the register names
 * based on the values of the constants.
 */
class RegisterConstantsHelper implements VM_RegisterConstants {
  static String[] gprNames() {
    String[] names = { "R0", "R1", "R2", "R3", "R4", "R5",
                       "R6", "R7","R8", "R9", "R10", "R11",
                       "R12", "R13","R14", "R15","R16", "R17",
                       "R18", "R19", "R20", "R21", "R22", "R23",
                       "R24", "R25", "R26", "R27", "R28", "R29", "R30", "R31"
    };    
    names[FRAME_POINTER] = "FP";
    names[JTOC_POINTER] = "JT";
    names[PROCESSOR_REGISTER] = "PR";
    return names;
  }

  static String[] fprNames() {
    return new String[] {
      "F0",  "F1",  "F2",  "F3",  "F4",  "F5",  "F6", "F7",
      "F8", "F9", "F10", "F11", "F12", "F13", "F14", "F15",
      "F16",  "F17",  "F18",  "F19",  "F20",  "F21",  "F22",  "F23",
      "F24",  "F25",  "F26",  "F27",  "F28",  "F29",  "F30",  "F31"
    };
  }
}

          
