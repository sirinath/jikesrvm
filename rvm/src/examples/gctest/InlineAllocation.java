/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import com.ibm.JikesRVM.*;
import com.ibm.JikesRVM.classloader.*;
import java.lang.reflect.*;

/**
 * A test to detect regressions in the quality of the optimizing compiler's
 * inline allocation sequence that is generated by inlining 
 * {@link VM_Entrypoints#resolvedNewScalarMethod} and 
 * {@link VM_Entrypoints#resolvedNewArrayMethod}.
 * 
 * This test will only run on Jikes RVM as it reaches into the
 * internals of the VM to get access to the generated machine code for
 * the prototypical methods.
 * 
 * It can also be used with OptTestHarness to manually inspect and 
 * tune the inline allocation sequence.
 *
 * @author Dave Grove
 */
class InlineAllocation {
  int a;
  int b;

  // We allow some pork for assertion checking in the inline allocation sequence,
  // However, we can't allow this to get out of hand...otherwise builds take way too long.
  static int assertionSpace = VM.VerifyAssertions ? (VM.BuildForIA32 ? 40 : 20) : 0;

  // Limits on sizes of allocation sequences.
  // Make them specific to the allocator so we can make them reasonably tight.
  //-#if RVM_WITH_SEMI_SPACE || RVM_WITH_GEN_COPY || RVM_WITH_GEN_MS || RVM_WITH_COPY_MS
  static int alloc1Limit = assertionSpace + (VM.BuildForIA32 ? 75 : 22); // small object
  static int alloc3Limit = assertionSpace + (VM.BuildForIA32 ? 100: 30); // large object
  //-#elif RVM_WITH_MARK_SWEEP
  static int alloc1Limit = assertionSpace + (VM.BuildForIA32 ? 100 : 30); // small object
  static int alloc3Limit = assertionSpace + (VM.BuildForIA32 ? 100 : 30); // large object
  //-#else
  static int alloc1Limit = assertionSpace + (VM.BuildForIA32 ? 100 : 30); // small object
  static int alloc3Limit = assertionSpace + (VM.BuildForIA32 ? 100 : 30); // large object
  //-#endif 
  static int alloc2Limit = alloc1Limit + (VM.BuildForIA32 ? 8 : 2); // small array.  Should be only the store of the length different than small object
  static int alloc4Limit = (VM.BuildForIA32 ? 40 : 10); // unknown size object. Should not be inlined at all.

  /**
   * A trivial method that should require the full prologue/epilogue
   * sequence (except that it won't use any nonvolatile registers...sigh).  
   * This enables us to subtract off the expected prologue/epilogue size
   * from the machine code size of the other methods.
   */
  int trivial(InlineAllocation x) {
    return x.a;
  }
  

  /**
   * Allocation a scalar
   */
  InlineAllocation alloc1() {
    return new InlineAllocation();
  }

  /**
   * Allocate an array of small, constant size
   */
  int[] alloc2() {
    return new int[10];
  }

  /**
   * Allocate an array of large, constant size
   */
  int[] alloc3() {
    return new int[100000];
  }

  /**
   * Allocate an array of unknown size
   */
  int [] alloc4(int size) {
    return new int[size];
  }

  /**
   * Force compilation of each of the methods and report on the size
   * of the generated machine code.
   */
  public static void main(String[] args) throws Exception {
    Class clazz = Class.forName("InlineAllocation");
    Method trivialJ = clazz.getDeclaredMethod("trivial", new Class[] { clazz });
    Method alloc1J = clazz.getDeclaredMethod("alloc1", null);
    Method alloc2J = clazz.getDeclaredMethod("alloc2", null);
    Method alloc3J = clazz.getDeclaredMethod("alloc3", null);
    Method alloc4J = clazz.getDeclaredMethod("alloc4", new Class[] {Integer.TYPE});

    VM_Method trivial = java.lang.reflect.JikesRVMSupport.getMethodOf(trivialJ);
    VM_Method alloc1 = java.lang.reflect.JikesRVMSupport.getMethodOf(alloc1J);
    VM_Method alloc2 = java.lang.reflect.JikesRVMSupport.getMethodOf(alloc2J);
    VM_Method alloc3 = java.lang.reflect.JikesRVMSupport.getMethodOf(alloc3J);
    VM_Method alloc4 = java.lang.reflect.JikesRVMSupport.getMethodOf(alloc4J);

    trivial.compile();
    int trivialSize = trivial.getCurrentCompiledMethod().numberOfInstructions();
    alloc1.compile();
    int alloc1Size = alloc1.getCurrentCompiledMethod().numberOfInstructions();
    alloc2.compile();
    int alloc2Size = alloc2.getCurrentCompiledMethod().numberOfInstructions();
    alloc3.compile();
    int alloc3Size = alloc3.getCurrentCompiledMethod().numberOfInstructions();
    alloc4.compile();
    int alloc4Size = alloc4.getCurrentCompiledMethod().numberOfInstructions();

    // System.out.println("Trivial method is "+trivialSize);
    // System.out.println("Scalar allocation size is "+alloc1Size);
    // System.out.println("Small array allocation is "+alloc2Size);
    // System.out.println("Large array allocation is "+alloc3Size);
    // System.out.println("Unknown size array allocation is "+alloc4Size);

    // Subtract off prologue/epilogue size. This is approximate!!!
    // If you really care, count the instructions by hand!
    alloc1Size -= trivialSize;
    alloc2Size -= trivialSize;
    alloc3Size -= trivialSize;
    alloc4Size -= trivialSize;

    System.out.println("Approximate scalar allocation size is "+alloc1Size);
    System.out.println("Approximate small array allocation is "+alloc2Size);
    System.out.println("Approximate large array allocation is "+alloc3Size);
    System.out.println("Approximate unknown size array allocation is "+alloc4Size);

    boolean fail = false;
    if (alloc1Size > alloc1Limit) {
      System.out.println("FAIL: scalar allocation is too porky");
      fail = true;
    }
    if (alloc2Size > alloc2Limit) {
      System.out.println("FAIL: small array allocation is too porky");
      fail = true;
    }
    if (alloc3Size > alloc3Limit) {
      System.out.println("FAIL: large array allocation is too porky");
      fail = true;
    }
    if (alloc4Size > alloc4Limit) {
      System.out.println("FAIL: unknown size array allocation is too porky");
      fail = true;
    }

    if (!fail) {
      System.out.println("Overall: SUCCESS");
    }
  }

}
