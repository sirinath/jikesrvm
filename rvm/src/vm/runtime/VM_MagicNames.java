/*
 * (C) Copyright IBM Corp 2001,2002
 */
//$Id$
package com.ibm.JikesRVM;

/**
 * Method names that are treated specially by compiler.
 * See also: VM_Magic, various magic compilers (eg VM_MagicCompiler)
 *
 * @author Bowen Alpern
 * @author Derek Lieber
 */
public class VM_MagicNames {
  //-----------//
  // interface //
  //-----------//
   
  public static VM_Atom sysCall0;                 
  public static VM_Atom sysCall1;                 
  public static VM_Atom sysCall2;
  public static VM_Atom sysCall3;
  public static VM_Atom sysCall4;
  public static VM_Atom sysCall_L_0;                 
  public static VM_Atom sysCall_L_I;                 
  public static VM_Atom sysCallAD;
  public static VM_Atom sysCallSigWait;
                 
  public static VM_Atom invokeMain;
  public static VM_Atom invokeClassInitializer;
  public static VM_Atom invokeMethodReturningVoid;
  public static VM_Atom invokeMethodReturningInt;
  public static VM_Atom invokeMethodReturningLong;
  public static VM_Atom invokeMethodReturningFloat;
  public static VM_Atom invokeMethodReturningDouble;
  public static VM_Atom invokeMethodReturningObject;

  public static VM_Atom getFramePointer;          
  public static VM_Atom getTocPointer;            
  public static VM_Atom setTocPointer;            
  public static VM_Atom getJTOC;
  public static VM_Atom getThreadId;
  public static VM_Atom setThreadId;

  public static VM_Atom getProcessorRegister;
  public static VM_Atom setProcessorRegister;
 
  //-#if RVM_FOR_IA32
  public static VM_Atom getESIAsProcessor;
  public static VM_Atom setESIAsProcessor;
  //-#endif
    
  public static VM_Atom getTime;
  public static VM_Atom getTimeBase;

  public static VM_Atom getCallerFramePointer;
  public static VM_Atom setCallerFramePointer;
  public static VM_Atom getCompiledMethodID;
  public static VM_Atom setCompiledMethodID;
  public static VM_Atom getNextInstructionAddress;
  public static VM_Atom setNextInstructionAddress;
  public static VM_Atom getReturnAddressLocation;

  public static VM_Atom getByteAtOffset;
  public static VM_Atom getIntAtOffset;
  public static VM_Atom getObjectAtOffset;
  public static VM_Atom getObjectArrayAtOffset;
  public static VM_Atom getLongAtOffset;
  public static VM_Atom setByteAtOffset;
  public static VM_Atom setIntAtOffset;
  public static VM_Atom setObjectAtOffset;
  public static VM_Atom setLongAtOffset;

  public static VM_Atom getMemoryWord;            
  public static VM_Atom setMemoryWord;            
  public static VM_Atom getMemoryAddress;
  public static VM_Atom setMemoryAddress;

  public static VM_Atom prepare;
  public static VM_Atom attempt;
    
  public static VM_Atom setThreadSwitchBit;
  public static VM_Atom clearThreadSwitchBit;
    
  public static VM_Atom saveThreadState;
  public static VM_Atom threadSwitch;
  public static VM_Atom restoreHardwareExceptionState;
  public static VM_Atom returnToNewStack;
  public static VM_Atom dynamicBridgeTo;
    
  public static VM_Atom objectAsAddress;          
  public static VM_Atom addressAsObject;          
  public static VM_Atom addressAsObjectArray;          
  public static VM_Atom addressAsType;
  public static VM_Atom objectAsType;
  public static VM_Atom addressAsByteArray;
  public static VM_Atom addressAsIntArray;
  public static VM_Atom objectAsByteArray;
  public static VM_Atom objectAsShortArray;
  public static VM_Atom objectAsIntArray;
  public static VM_Atom addressAsThread;
  public static VM_Atom objectAsThread;
  public static VM_Atom objectAsProcessor;
//-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
  public static VM_Atom addressAsBlockControl;
  public static VM_Atom addressAsSizeControl;
  public static VM_Atom addressAsSizeControlArray;
//-#endif
  public static VM_Atom threadAsCollectorThread;
  public static VM_Atom addressAsRegisters;
  public static VM_Atom addressAsStack;
  public static VM_Atom floatAsIntBits;           
  public static VM_Atom intBitsAsFloat;           
  public static VM_Atom doubleAsLongBits;         
  public static VM_Atom longBitsAsDouble;
             
  public static VM_Atom getObjectType;            
  public static VM_Atom getArrayLength;           
   
  public static VM_Atom sync;
  public static VM_Atom isync;
  //-#if RVM_FOR_POWERPC
  public static VM_Atom dcbst;
  public static VM_Atom icbi;
  //-#endif
  //-#if RVM_FOR_IA32
  public static VM_Atom roundToZero;
  public static VM_Atom clearFloatingPointState;
  //-#endif

  public static VM_Atom pragmaNoOptCompile;

  // Names associated with classes VM_Word, VM_Address, and VM_Offset
  //
  public static VM_Atom wordFromInt;
  public static VM_Atom wordToInt;
  public static VM_Atom wordToWord;
  public static VM_Atom wordToAddress;
  public static VM_Atom wordAdd;
  public static VM_Atom wordSub;
  public static VM_Atom wordDiff;
  public static VM_Atom wordLT;
  public static VM_Atom wordLE;
  public static VM_Atom wordEQ;
  public static VM_Atom wordNE;
  public static VM_Atom wordGT;
  public static VM_Atom wordGE;
  public static VM_Atom wordZero;
  public static VM_Atom wordMax;
  public static VM_Atom wordIsZero;
  public static VM_Atom wordIsMax;
  public static VM_Atom wordAnd;
  public static VM_Atom wordOr;
  public static VM_Atom wordNot;
  public static VM_Atom wordXor;


   //----------------//
   // implementation //
   //----------------//
   
  public static void init() {
    sysCall0                      = VM_Atom.findOrCreateAsciiAtom("sysCall0");
    sysCall1                      = VM_Atom.findOrCreateAsciiAtom("sysCall1");
    sysCall2                      = VM_Atom.findOrCreateAsciiAtom("sysCall2");
    sysCall3                      = VM_Atom.findOrCreateAsciiAtom("sysCall3");
    sysCall4                      = VM_Atom.findOrCreateAsciiAtom("sysCall4");
    sysCall_L_0                   = VM_Atom.findOrCreateAsciiAtom("sysCall_L_0");
    sysCall_L_I                   = VM_Atom.findOrCreateAsciiAtom("sysCall_L_I");
    sysCallAD  		          = VM_Atom.findOrCreateAsciiAtom("sysCallAD");
    sysCallSigWait		  = VM_Atom.findOrCreateAsciiAtom("sysCallSigWait");

    invokeMain                    = VM_Atom.findOrCreateAsciiAtom("invokeMain");
    invokeClassInitializer        = VM_Atom.findOrCreateAsciiAtom("invokeClassInitializer");
    invokeMethodReturningVoid     = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningVoid");
    invokeMethodReturningInt      = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningInt");
    invokeMethodReturningLong     = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningLong");
    invokeMethodReturningFloat    = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningFloat");
    invokeMethodReturningDouble   = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningDouble");
    invokeMethodReturningObject   = VM_Atom.findOrCreateAsciiAtom("invokeMethodReturningObject");

    getFramePointer               = VM_Atom.findOrCreateAsciiAtom("getFramePointer");
    getTocPointer                 = VM_Atom.findOrCreateAsciiAtom("getTocPointer");
    setTocPointer                 = VM_Atom.findOrCreateAsciiAtom("setTocPointer");
    getJTOC                       = VM_Atom.findOrCreateAsciiAtom("getJTOC");
    getThreadId                   = VM_Atom.findOrCreateAsciiAtom("getThreadId");
    setThreadId                   = VM_Atom.findOrCreateAsciiAtom("setThreadId");
    getProcessorRegister          = VM_Atom.findOrCreateAsciiAtom("getProcessorRegister");
    setProcessorRegister          = VM_Atom.findOrCreateAsciiAtom("setProcessorRegister");
    
    //-#if RVM_FOR_IA32
    getESIAsProcessor = VM_Atom.findOrCreateAsciiAtom("getESIAsProcessor");
    setESIAsProcessor = VM_Atom.findOrCreateAsciiAtom("setESIAsProcessor");
    //-#endif

    getTime                       = VM_Atom.findOrCreateAsciiAtom("getTime");
    getTimeBase                   = VM_Atom.findOrCreateAsciiAtom("getTimeBase");

    getCallerFramePointer         = VM_Atom.findOrCreateAsciiAtom("getCallerFramePointer");
    setCallerFramePointer         = VM_Atom.findOrCreateAsciiAtom("setCallerFramePointer");
    getCompiledMethodID           = VM_Atom.findOrCreateAsciiAtom("getCompiledMethodID");
    setCompiledMethodID           = VM_Atom.findOrCreateAsciiAtom("setCompiledMethodID");
    getNextInstructionAddress     = VM_Atom.findOrCreateAsciiAtom("getNextInstructionAddress");
    setNextInstructionAddress     = VM_Atom.findOrCreateAsciiAtom("setNextInstructionAddress");
    getReturnAddressLocation      = VM_Atom.findOrCreateAsciiAtom("getReturnAddressLocation");

    getByteAtOffset               = VM_Atom.findOrCreateAsciiAtom("getByteAtOffset");
    getIntAtOffset                = VM_Atom.findOrCreateAsciiAtom("getIntAtOffset");
    getObjectAtOffset             = VM_Atom.findOrCreateAsciiAtom("getObjectAtOffset");
    getObjectArrayAtOffset        = VM_Atom.findOrCreateAsciiAtom("getObjectArrayAtOffset");
    getLongAtOffset               = VM_Atom.findOrCreateAsciiAtom("getLongAtOffset");
    setByteAtOffset               = VM_Atom.findOrCreateAsciiAtom("setByteAtOffset");
    setIntAtOffset                = VM_Atom.findOrCreateAsciiAtom("setIntAtOffset");
    setObjectAtOffset             = VM_Atom.findOrCreateAsciiAtom("setObjectAtOffset");
    setLongAtOffset               = VM_Atom.findOrCreateAsciiAtom("setLongAtOffset");

    getMemoryWord                 = VM_Atom.findOrCreateAsciiAtom("getMemoryWord");
    setMemoryWord                 = VM_Atom.findOrCreateAsciiAtom("setMemoryWord");
    getMemoryAddress              = VM_Atom.findOrCreateAsciiAtom("getMemoryAddress");
    setMemoryAddress              = VM_Atom.findOrCreateAsciiAtom("setMemoryAddress");

    prepare                       = VM_Atom.findOrCreateAsciiAtom("prepare");
    attempt                       = VM_Atom.findOrCreateAsciiAtom("attempt");

    setThreadSwitchBit            = VM_Atom.findOrCreateAsciiAtom("setThreadSwitchBit");
    clearThreadSwitchBit          = VM_Atom.findOrCreateAsciiAtom("clearThreadSwitchBit");
    
    saveThreadState               = VM_Atom.findOrCreateAsciiAtom("saveThreadState");
    threadSwitch                  = VM_Atom.findOrCreateAsciiAtom("threadSwitch");
    restoreHardwareExceptionState = VM_Atom.findOrCreateAsciiAtom("restoreHardwareExceptionState");
    returnToNewStack              = VM_Atom.findOrCreateAsciiAtom("returnToNewStack");
    dynamicBridgeTo               = VM_Atom.findOrCreateAsciiAtom("dynamicBridgeTo");
      
    objectAsAddress               = VM_Atom.findOrCreateAsciiAtom("objectAsAddress");
    addressAsObject               = VM_Atom.findOrCreateAsciiAtom("addressAsObject");
    addressAsObjectArray          = VM_Atom.findOrCreateAsciiAtom("addressAsObjectArray");
    addressAsType                 = VM_Atom.findOrCreateAsciiAtom("addressAsType");
    objectAsType                  = VM_Atom.findOrCreateAsciiAtom("objectAsType");
    addressAsByteArray            = VM_Atom.findOrCreateAsciiAtom("addressAsByteArray");
    addressAsIntArray             = VM_Atom.findOrCreateAsciiAtom("addressAsIntArray");
    objectAsByteArray             = VM_Atom.findOrCreateAsciiAtom("objectAsByteArray");
    objectAsShortArray            = VM_Atom.findOrCreateAsciiAtom("objectAsShortArray");
    objectAsIntArray              = VM_Atom.findOrCreateAsciiAtom("objectAsIntArray");

    addressAsThread               = VM_Atom.findOrCreateAsciiAtom("addressAsThread");
    objectAsThread                = VM_Atom.findOrCreateAsciiAtom("objectAsThread");
    objectAsProcessor             = VM_Atom.findOrCreateAsciiAtom("objectAsProcessor");
  //-#if RVM_WITH_JIKESRVM_MEMORY_MANAGERS
    addressAsBlockControl         = VM_Atom.findOrCreateAsciiAtom("addressAsBlockControl");
    addressAsSizeControl          = VM_Atom.findOrCreateAsciiAtom("addressAsSizeControl");
    addressAsSizeControlArray     = VM_Atom.findOrCreateAsciiAtom("addressAsSizeControlArray");
  //-#endif
    threadAsCollectorThread       = VM_Atom.findOrCreateAsciiAtom("threadAsCollectorThread");
    addressAsRegisters            = VM_Atom.findOrCreateAsciiAtom("addressAsRegisters");
    addressAsStack                = VM_Atom.findOrCreateAsciiAtom("addressAsStack");
    floatAsIntBits                = VM_Atom.findOrCreateAsciiAtom("floatAsIntBits");
    intBitsAsFloat                = VM_Atom.findOrCreateAsciiAtom("intBitsAsFloat");
    doubleAsLongBits              = VM_Atom.findOrCreateAsciiAtom("doubleAsLongBits");
    longBitsAsDouble              = VM_Atom.findOrCreateAsciiAtom("longBitsAsDouble");
      
    getObjectType                 = VM_Atom.findOrCreateAsciiAtom("getObjectType");
    getArrayLength                = VM_Atom.findOrCreateAsciiAtom("getArrayLength");

    sync                          = VM_Atom.findOrCreateAsciiAtom("sync");
    isync                         = VM_Atom.findOrCreateAsciiAtom("isync");
    //-#if RVM_FOR_POWERPC
    dcbst                         = VM_Atom.findOrCreateAsciiAtom("dcbst");
    icbi                          = VM_Atom.findOrCreateAsciiAtom("icbi");
    //-#endif
    //-#if RVM_FOR_IA32
    roundToZero                   = VM_Atom.findOrCreateAsciiAtom("roundToZero");
    clearFloatingPointState       = VM_Atom.findOrCreateAsciiAtom("clearFloatingPointState");
    //-#endif

    pragmaNoOptCompile         = VM_Atom.findOrCreateAsciiAtom("pragmaNoOptCompile");

    wordFromInt                = VM_Atom.findOrCreateAsciiAtom("fromInt");
    wordToInt                  = VM_Atom.findOrCreateAsciiAtom("toInt");
    wordToWord                 = VM_Atom.findOrCreateAsciiAtom("toWord");
    wordToAddress              = VM_Atom.findOrCreateAsciiAtom("toAddress");
    wordAdd                    = VM_Atom.findOrCreateAsciiAtom("add");
    wordSub                    = VM_Atom.findOrCreateAsciiAtom("sub");
    wordDiff                   = VM_Atom.findOrCreateAsciiAtom("diff");
    wordLT                     = VM_Atom.findOrCreateAsciiAtom("LT");
    wordLE                     = VM_Atom.findOrCreateAsciiAtom("LE");
    wordEQ                     = VM_Atom.findOrCreateAsciiAtom("EQ");
    wordNE                     = VM_Atom.findOrCreateAsciiAtom("NE");
    wordGT                     = VM_Atom.findOrCreateAsciiAtom("GT");
    wordGE                     = VM_Atom.findOrCreateAsciiAtom("GE");
    wordZero                   = VM_Atom.findOrCreateAsciiAtom("zero");
    wordMax                    = VM_Atom.findOrCreateAsciiAtom("max");
    wordIsZero                 = VM_Atom.findOrCreateAsciiAtom("isZero");
    wordIsMax                  = VM_Atom.findOrCreateAsciiAtom("isMax");
    wordAnd                    = VM_Atom.findOrCreateAsciiAtom("and");
    wordOr                     = VM_Atom.findOrCreateAsciiAtom("or");
    wordNot                    = VM_Atom.findOrCreateAsciiAtom("not");
    wordXor                    = VM_Atom.findOrCreateAsciiAtom("xor");
  }
}


