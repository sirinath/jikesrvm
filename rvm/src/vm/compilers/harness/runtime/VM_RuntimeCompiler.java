/*
 * (C) Copyright IBM Corp. 2001
 */
// $Id$
package com.ibm.JikesRVM;

import com.ibm.JikesRVM.classloader.*;
//-#if RVM_WITH_ADAPTIVE_SYSTEM
import com.ibm.JikesRVM.opt.*;
import com.ibm.JikesRVM.adaptive.*;
//-#endif

/**
 * Harness to select which compiler to dynamically
 * compile a method in first invocation.
 * 
 * A place to put code common to all runtime compilers.
 * This includes instrumentation code to get equivalent data for
 * each of the runtime compilers.
 * <p>
 * We collect the following data for each compiler
 * <ol>
 * <li>
 *   total number of methods complied by the compiler
 * <li>
 *   total compilation time in milliseconds.
 * <li>
 *   total number of bytes of bytecodes compiled by the compiler
 *   (under the assumption that there is no padding in the bytecode
 *   array and thus VM_Method.getBytecodes().length is the number bytes
 *   of bytecode for a method)
 * <li>
 *   total number of machine code insructions generated by the compiler
 *   (under the assumption that there is no (excessive) padding in the
 *   machine code array and thus VM_Method.getInstructions().length
 *   is a close enough approximation of the number of machinecodes generated)
 * </ol>
 *   Note that even if 3. & 4. are inflated due to padding, the numbers will 
 *   still be an accurate measure of the space costs of the compile-only 
 *   approach.
 * 
 * @author Matthew Arnold
 * @author Dave Grove
 * @author Michael Hind
 */
public class VM_RuntimeCompiler implements VM_Constants, 
                                           VM_Callbacks.ExitMonitor {

  // Use these to encode the compiler for record()
  public static final byte JNI_COMPILER      = 0;
  public static final byte BASELINE_COMPILER = 1;
  public static final byte OPT_COMPILER      = 2;

  // Data accumulators
  private static final String name[]        = {"JNI\t","Base\t","Opt\t"};   // Output names
  private static int totalMethods[]         = {0,0,0};
  private static double totalCompTime[]     = {0,0,0}; 
  private static int totalBCLength[]        = {0,0,0};
  private static int totalMCLength[]        = {0,0,0};

  // running sum of the natural logs of the rates, 
  //  used for geometric mean, the product of rates is too big for doubles
  //  so we use the principle of logs to help us 
  // We compute  e ** ((log a + log b + ... + log n) / n )
  private static double totalLogOfRates[]   = {0,0,0};

  // We can't record values until Math.log is loaded, so we miss the first few
  private static int totalLogValueMethods[] = {0,0,0};

  //-#if RVM_WITH_ADAPTIVE_SYSTEM
  public static OPT_InlineOracle offlineInlineOracle;
  private static String[] earlyOptArgs = new String[0];

  // is the opt compiler usable?
  protected static boolean compilerEnabled;  
  
  // is opt compiler currently in use?
  // This flag is used to detect/avoid recursive opt compilation.
  // (ie when opt compilation causes a method to be compiled).
  // We also make all public entrypoints static synchronized methods 
  // because the opt compiler is not reentrant. 
  // When we actually fix defect 2912, we'll have to implement a different
  // scheme that can distinguish between recursive opt compilation by the same
  // thread (always bad) and parallel opt compilation (currently bad, future ok).
  // NOTE: This code can be quite subtle, so please be absolutely sure
  // you know what you're doing before modifying it!!!
  protected static boolean compilationInProgress; 
  
  // One time check to optionally preload and compile a specified class
  protected static boolean preloadChecked = false;

  // Cache objects needed to cons up compilation plans
  public static OPT_Options options;
  public static OPT_OptimizationPlanElement[] optimizationPlan;
  //-#endif

  /**
   * To be called when the VM is about to exit.
   * @param value the exit value
   */
  public void notifyExit(int value) {
    report(false);
  }

  /**
   * This method records the time and sizes (bytecode and machine code) for
   * a compilation.
   * @param compiler the compiler used
   * @param method the resulting VM_Method
   * @param compiledMethod the resulting compiled method
   */
  public static void record(byte compiler, 
                            VM_NormalMethod method, 
                            VM_CompiledMethod compiledMethod) {

    recordCompilation(compiler, method.getBytecodeLength(),
                      compiledMethod.getInstructions().length(),
                      compiledMethod.getCompilationTime());

    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    if (VM.LogAOSEvents) {
      if (VM_AOSLogging.booted()) {
        VM_AOSLogging.recordUpdatedCompilationRates(compiler, 
                                                    method,
                                                    method.getBytecodeLength(),
                                                    totalBCLength[compiler],              
                                                    compiledMethod.getInstructions().length(),
                                                    totalMCLength[compiler],
                                                    compiledMethod.getCompilationTime(),
                                                    totalCompTime[compiler],
                                                    totalLogOfRates[compiler],
                                                    totalLogValueMethods[compiler],
                                                    totalMethods[compiler]);
      }
    }
    //-#endif
  }

  /**
   * This method records the time and sizes (bytecode and machine code) for
   * a compilation
   * @param compiler the compiler used
   * @param method the resulting VM_Method
   * @param compiledMethod the resulting compiled method
   */
  public static void record(byte compiler, 
                            VM_NativeMethod method, 
                            VM_CompiledMethod compiledMethod) {


    recordCompilation(compiler, 
                      0, // don't have any bytecode info, its native
                      compiledMethod.getInstructions().length(),
                      compiledMethod.getCompilationTime());
  }

  /**
   * This method does the actual recording
   * @param compiler the compiler used
   * @param BCLength the number of bytecodes in method source
   * @param MCLength the length of the generated machine code
   * @param compTime the compilation time in ms
   */
  private static void recordCompilation(byte compiler, 
                                        int BCLength, 
                                        int MCLength, 
                                        double compTime) {

    totalMethods[compiler]++;
    totalMCLength[compiler] += MCLength;
    totalCompTime[compiler] += compTime;

    // Comp rate not useful for JNI compiler because there is no bytecode!
    if (compiler != JNI_COMPILER) {
      totalBCLength[compiler] += BCLength; 
      double rate = BCLength / compTime;

      // need to be fully booted before calling log
      if (VM.fullyBooted) {
        // we want the geometric mean, but the product of rates is too big 
        //  for doubles, so we use the principle of logs to help us 
        // We compute  e ** ((log a + log b + ... + log n) / n )
        totalLogOfRates[compiler] += Math.log(rate);
        totalLogValueMethods[compiler]++;
      }
    }
  }

  /**
   * This method produces a summary report of compilation activities
   * @param explain Explains the metrics used in the report
   */
  public static void report (boolean explain) { 
    VM.sysWrite("\n\t\tCompilation Subsystem Report\n");
    VM.sysWrite("Comp\t#Meths\tTime\tbcb/ms\tmcb/bcb\tMCKB\tBCKB\n");
    for (int i=JNI_COMPILER; i<=OPT_COMPILER; i++) {
      if (totalMethods[i]>0) {
        VM.sysWrite(name[i]);
        // Number of methods
        VM.sysWrite(totalMethods[i]);
        VM.sysWrite("\t");
        // Compilation time
        VM.sysWrite(totalCompTime[i]);
        VM.sysWrite("\t");

        if (i == JNI_COMPILER) {
          VM.sysWrite("NA");
        } else {
          // Bytecode bytes per millisecond, 
          //  use unweighted geomean 
          VM.sysWrite(Math.exp(totalLogOfRates[i] / totalLogValueMethods[i]), 2);
        }
        VM.sysWrite("\t");
        // Ratio of machine code bytes to bytecode bytes
        if (i != JNI_COMPILER) {
          VM.sysWrite((double)(totalMCLength[i] << LG_INSTRUCTION_WIDTH)/(double)totalBCLength[i], 2);
        } else {
          VM.sysWrite("NA");
        }
        VM.sysWrite("\t");
        // Generated machine code Kbytes
        VM.sysWrite((double)(totalMCLength[i] << LG_INSTRUCTION_WIDTH)/1024, 1);
        VM.sysWrite("\t");
        // Compiled bytecode Kbytes
        if (i != JNI_COMPILER) {
          VM.sysWrite((double)totalBCLength[i]/1024, 1); 
        } else {
          VM.sysWrite("NA");
        }
        VM.sysWrite("\n");
      }
    }
    if (explain) {
      // Generate an explanation of the metrics reported
      VM.sysWrite("\t\t\tExplanation of Metrics\n");
      VM.sysWrite("#Meths:\t\tTotal number of methods compiled by the compiler\n");
      VM.sysWrite("Time:\t\tTotal compilation time in milliseconds\n");
      VM.sysWrite("bcb/ms:\t\tNumber of bytecode bytes complied per millisecond\n");
      VM.sysWrite("mcb/bcb:\tRatio of machine code bytes to bytecode bytes\n");
      VM.sysWrite("MCKB:\t\tTotal number of machine code bytes generated in kilobytes\n");
      VM.sysWrite("BCKB:\t\tTotal number of bytecode bytes compiled in kilobytes\n");
    }

    VM_BaselineCompiler.generateBaselineCompilerSubsystemReport(explain);

    //-#if RVM_WITH_ADAPTIVE_SYSTEM 
    // Get the opt's report
    VM_TypeReference theTypeRef = VM_TypeReference.findOrCreate(VM_SystemClassLoader.getVMClassLoader(),
                                                                VM_Atom.findOrCreateAsciiAtom("Lcom/ibm/JikesRVM/opt/OPT_OptimizationPlanner;"));
    VM_Type theType = theTypeRef.peekResolvedType();
    if (theType != null && theType.asClass().isInitialized()) {
      OPT_OptimizationPlanner.generateOptimizingCompilerSubsystemReport(explain);
    } else {
      VM.sysWrite("\n\tNot generating Optimizing Compiler SubSystem Report because \n");
      VM.sysWrite("\tthe opt compiler was never invoked.\n\n");
    }
    //-#endif
  }
   
  /**
   * Return the current estimate of basline-compiler rate, in bcb/msec
   */
  public static double getBaselineRate() {
    return Math.exp(totalLogOfRates[BASELINE_COMPILER] / totalLogValueMethods[BASELINE_COMPILER]);
  }

  /**
   * This method will compile the passed method using the baseline compiler.
   * @param method the method to compile
   */
  public static VM_CompiledMethod baselineCompile(VM_NormalMethod method) {
    VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.BASELINE);
    long start = 0;
    if (VM.MeasureCompilation || VM.BuildForAdaptiveSystem) {
      start = VM_Thread.getCurrentThread().accumulateCycles();
    }

    VM_CompiledMethod cm = VM_BaselineCompiler.compile(method);

    if (VM.MeasureCompilation || VM.BuildForAdaptiveSystem) {
      long end = VM_Thread.getCurrentThread().accumulateCycles();
      double compileTime = VM_Time.cyclesToMillis(end - start);
      cm.setCompilationTime(compileTime);
      record(BASELINE_COMPILER, method, cm);
    }
    
    return cm;
  }

  //-#if RVM_WITH_ADAPTIVE_SYSTEM
  /**
   * Process command line argument destined for the opt compiler
   */
  public static void processOptCommandLineArg(String prefix, String arg) {
    if (compilerEnabled) {
      if (options.processAsOption(prefix, arg)) {
        // update the optimization plan to reflect the new command line argument
        setNoCacheFlush(options);
        optimizationPlan = OPT_OptimizationPlanner.createOptimizationPlan(options);
      } else {
        VM.sysWrite("Unrecognized opt compiler argument \""+arg+"\"");
        VM.sysExit(VM.exitStatusBogusCommandLineArg);
      }
    } else {
      String[] tmp = new String[earlyOptArgs.length+2];
      for (int i=0; i<earlyOptArgs.length; i++) {
        tmp[i] = earlyOptArgs[i];
      }
      earlyOptArgs = tmp;
      earlyOptArgs[earlyOptArgs.length-2] = prefix;
      earlyOptArgs[earlyOptArgs.length-1] = arg;
    }
  }

  /**
   * attempt to compile the passed method with the OPT_Compiler.
   * Don't handle OPT_OptimizingCompilerExceptions 
   *   (leave it up to caller to decide what to do)
   * Precondition: compilationInProgress "lock" has been acquired
   * @param method the method to compile
   * @param plan the plan to use for compiling the method
   */
  private static VM_CompiledMethod optCompile(VM_NormalMethod method, 
                                              OPT_CompilationPlan plan) 
    throws OPT_OptimizingCompilerException {
    if (VM.VerifyAssertions) {
      VM._assert(compilationInProgress, "Failed to acquire compilationInProgress \"lock\"");
    }
    
    VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.JNI);
    long start = 0;
    if (VM.MeasureCompilation || VM.BuildForAdaptiveSystem) {
      start = VM_Thread.getCurrentThread().accumulateCycles();
    }
    
    VM_CompiledMethod cm = OPT_Compiler.compile(plan);

    if (VM.MeasureCompilation || VM.BuildForAdaptiveSystem) {
      long end = VM_Thread.getCurrentThread().accumulateCycles();
      double compileTime = VM_Time.cyclesToMillis(end - start);
      cm.setCompilationTime(compileTime);
      record(OPT_COMPILER, method, cm);
    }
    
    return cm;
  }
  

  // These methods are safe to invoke from VM_RuntimeCompiler.compile

  /**
   * This method tries to compile the passed method with the OPT_Compiler, 
   * using the default compilation plan.  If
   * this fails we will use the quicker compiler (baseline for now)
   * The following is carefully crafted to avoid (infinte) recursive opt
   * compilation for all combinations of bootimages & lazy/eager compilation.
   * Be absolutely sure you know what you're doing before changing it !!!
   * @param method the method to compile
   */
  public static synchronized VM_CompiledMethod optCompileWithFallBack(VM_NormalMethod method) {
    if (compilationInProgress) {
      return fallback(method);
    } else {
      try {
        compilationInProgress = true;
        OPT_CompilationPlan plan = new OPT_CompilationPlan(method, optimizationPlan, null, options);
        return optCompileWithFallBackInternal(method, plan);
      } finally {
        compilationInProgress = false;
      }
    }
  }

  /**
   * This method tries to compile the passed method with the OPT_Compiler
   * with the passed compilation plan.  If
   * this fails we will use the quicker compiler (baseline for now)
   * The following is carefully crafted to avoid (infinte) recursive opt
   * compilation for all combinations of bootimages & lazy/eager compilation.
   * Be absolutely sure you know what you're doing before changing it !!!
   * @param method the method to compile
   * @param plan the compilation plan to use for the compile
   */
  public static synchronized VM_CompiledMethod optCompileWithFallBack(VM_NormalMethod method, 
                                                                      OPT_CompilationPlan plan) {
    if (compilationInProgress) {
      return fallback(method);
    } else {
      try {
        compilationInProgress = true;
        return optCompileWithFallBackInternal(method, plan);
      } finally {
        compilationInProgress = false;
      }
    }
  }

  /**
   * This real method that performs the opt compilation.  
   * @param method the method to compile
   * @param plan the compilation plan to use
   */
  private static VM_CompiledMethod optCompileWithFallBackInternal(VM_NormalMethod method, 
                                                                  OPT_CompilationPlan plan) {
    if (method.hasNoOptCompilePragma()) return fallback(method);
    try {
      return optCompile(method, plan);
    } catch (OPT_OptimizingCompilerException e) {
      String msg = "VM_RuntimeCompiler: can't optimize \"" + method + "\" (error was: " + e + "): reverting to baseline compiler\n"; 
      if (e.isFatal && VM.ErrorsFatal) {
        e.printStackTrace();
        VM.sysFail(msg);
      } else {
        boolean printMsg = true;
        if (e instanceof OPT_MagicNotImplementedException) {
          printMsg = !((OPT_MagicNotImplementedException)e).isExpected;
        }
        if (printMsg) VM.sysWrite(msg);
      }
      return fallback(method);
    } 
  }


  //-#if RVM_WITH_OSR
  /* recompile the specialized method with OPT_Compiler. */ 
  public static VM_CompiledMethod recompileWithOptOnStackSpecialization(OPT_CompilationPlan plan) {
    if (VM.VerifyAssertions) { VM._assert(plan.method.isForOsrSpecialization());}
    if (compilationInProgress) {
      return null;
    }

    try {
      compilationInProgress = true;

      // the compiler will check if isForOsrSpecialization of the method
      VM_CompiledMethod cm = optCompile(plan.method, plan);

      // we donot replace the compiledMethod of original method, 
      // because it is temporary method
      return cm;
    } catch (OPT_OptimizingCompilerException e) {
      e.printStackTrace();
      String msg = "Optimizing compiler " 
        +"(via recompileWithOptOnStackSpecialization): "
        +"can't optimize \"" + plan.method + "\" (error was: " + e + ")\n"; 

      if (e.isFatal && VM.ErrorsFatal) {
        VM.sysFail(msg);
      } else {
        VM.sysWrite(msg);
      }
      return null;      
    } finally {
      compilationInProgress = false;
    }
  }
  //-#endif 


  /**
   * This method tries to compile the passed method with the OPT_Compiler.
   * It will install the new compiled method in the VM, if sucessful.
   * NOTE: the recompile method should never be invoked via 
   *      VM_RuntimeCompiler.compile;
   *   it does not have sufficient guards against recursive recompilation.
   * @param plan the compilation plan to use
   * @return the CMID of the new method if successful, -1 if the 
   *    recompilation failed.
   *
   **/
  public static synchronized int recompileWithOpt(OPT_CompilationPlan plan) {
    if (compilationInProgress) {
      return -1;
    } else {
      try {
        compilationInProgress = true;
        VM_CompiledMethod cm = optCompile(plan.method, plan);
        try {
          plan.method.replaceCompiledMethod(cm);
        } catch (Throwable e) {
          String msg = "Failure in VM_Method.replaceCompiledMethod (via recompileWithOpt): while replacing \"" + plan.method + "\" (error was: " + e + ")\n"; 
          if (VM.ErrorsFatal) {
            e.printStackTrace();
            VM.sysFail(msg);
          } else {
            VM.sysWrite(msg);
          }
          return -1;
        }
        return cm.getId();
      } catch (OPT_OptimizingCompilerException e) {
        String msg = "Optimizing compiler (via recompileWithOpt): can't optimize \"" + plan.method + "\" (error was: " + e + ")\n"; 
        if (e.isFatal && VM.ErrorsFatal) {
          e.printStackTrace();
          VM.sysFail(msg);
        } else {
          // VM.sysWrite(msg);
        }
        return -1;
      } finally {
        compilationInProgress = false;
      }
    }
  }

  /**
   * A wrapper method for those callers who don't want to make
   * optimization plans
   * @param method the method to recompile
   */
  public static int recompileWithOpt(VM_NormalMethod method) {
    OPT_CompilationPlan plan = new OPT_CompilationPlan(method, 
                                                       optimizationPlan, 
                                                       null, 
                                                       options);
    return recompileWithOpt(plan);
  }

  /**
   * This method uses the default compiler (baseline) to compile a method
   * It is typically called when a more aggressive compilation fails.
   * This method is safe to invoke from VM_RuntimeCompiler.compile
   */
  protected static VM_CompiledMethod fallback(VM_NormalMethod method) {
    // call the inherited method "baselineCompile"
    return baselineCompile(method);
  }

  /**
   * This method detect if we're running on a uniprocessor and optimizes
   * accordingly.
   * One needs to call this method each time a command line argument is changed
   * and each time an OPT_Options object is created.
   * @param options the command line options for the opt compiler
  */
  public static void setNoCacheFlush(OPT_Options options) {
    if (options.DETECT_UNIPROCESSOR) {
      if (VM_SysCall.sysNumProcessors() == 1) {
        options.NO_CACHE_FLUSH = true;
      }
    }
  }
  //-#endif

  
  public static void boot() {
    if (VM.MeasureCompilation) {
      VM_Callbacks.addExitMonitor(new VM_RuntimeCompiler());
    }
    //-#if RVM_WITH_ADAPTIVE_SYSTEM
    options = new OPT_Options();
    setNoCacheFlush(options);

    optimizationPlan = OPT_OptimizationPlanner.createOptimizationPlan(options);
    if (VM.MeasureCompilation) {
      OPT_OptimizationPlanner.initializeMeasureCompilation();
    }

    OPT_Compiler.init(options);

    // when we reach here the OPT compiler is enabled.
    compilerEnabled = true;

    for (int i=0; i<earlyOptArgs.length; i+=2) {
      processOptCommandLineArg(earlyOptArgs[i], earlyOptArgs[i+1]);
    }
    //-#endif
  }
  
  public static void processCommandLineArg(String prefix, String arg) {
    //-#if !RVM_WITH_ADAPTIVE_SYSTEM
    VM_BaselineCompiler.processCommandLineArg(prefix, arg);
    //-#else
    if (VM_Controller.options !=null  && VM_Controller.options.optIRC()) {
      processOptCommandLineArg(prefix, arg);
    } else {
      VM_BaselineCompiler.processCommandLineArg(prefix, arg);
    }
    //-#endif
  }
  
  /**
   * Compile a Java method when it is first invoked.
   * @param method the method to compile
   * @return its compiled method.
   */
  public static VM_CompiledMethod compile(VM_NormalMethod method) {
    //-#if !RVM_WITH_ADAPTIVE_SYSTEM
    return baselineCompile(method);
    //-#else
    VM_CompiledMethod cm;
    if (!VM_Controller.enabled) {
      // System still early in boot process; compile with baseline compiler
      cm = baselineCompile(method);
      VM_ControllerMemory.incrementNumBase();
    } else {
      if (!preloadChecked) {
        preloadChecked = true;                  // prevent subsequent calls
        // N.B. This will use irc options
        if (VM_BaselineCompiler.options.PRELOAD_CLASS != null) {
          compilationInProgress = true;         // use baseline during preload
          // Other than when boot options are requested (processed during preloadSpecialClass
          // It is hard to communicate options for these special compilations. Use the 
          // default options and at least pick up the verbose if requested for base/irc
          OPT_Options tmpoptions = (OPT_Options)options.clone();
          tmpoptions.PRELOAD_CLASS = VM_BaselineCompiler.options.PRELOAD_CLASS;
          tmpoptions.PRELOAD_AS_BOOT = VM_BaselineCompiler.options.PRELOAD_AS_BOOT;
          if (VM_BaselineCompiler.options.PRINT_METHOD) {
            tmpoptions.PRINT_METHOD = true;
          } else {
            tmpoptions = options;
          }
          OPT_Compiler.preloadSpecialClass(tmpoptions);
          compilationInProgress = false;
        }
      }
      if (VM_Controller.options.optIRC()) {
        if (// will only run once: don't bother optimizing
            method.isClassInitializer() || 
            // exception in progress. can't use opt compiler: 
            // it uses exceptions and runtime doesn't support 
            // multiple pending (undelivered) exceptions [--DL]
            VM_Thread.getCurrentThread().hardwareExceptionRegisters.inuse) {
          // compile with baseline compiler
          cm = baselineCompile(method);
          VM_ControllerMemory.incrementNumBase();
        } else { // compile with opt compiler
          VM_AOSInstrumentationPlan instrumentationPlan = 
            new VM_AOSInstrumentationPlan(VM_Controller.options, method);
          OPT_CompilationPlan compPlan = 
            new OPT_CompilationPlan(method, optimizationPlan, 
                                    instrumentationPlan, options);
          if (offlineInlineOracle != null) {
            compPlan.setInlineOracle(offlineInlineOracle);
          }
          cm = optCompileWithFallBack(method, compPlan);
        }
      } else {
        if (VM_Controller.options.BACKGROUND_RECOMPILATION) {
          // must be an inital compilation: compile with baseline compiler
          cm = baselineCompile(method);
          VM_ControllerMemory.incrementNumBase();
        } else {
          // check to see if there is a compilation plan for this method.
          VM_ControllerPlan plan = VM_ControllerMemory.findLatestPlan(method);
          if (plan == null || plan.getStatus() != VM_ControllerPlan.IN_PROGRESS) {
            // initial compilation or some other funny state: compile with baseline compiler
            cm = baselineCompile(method);
            VM_ControllerMemory.incrementNumBase();
          } else {
            cm = plan.doRecompile();
            if (cm == null) {
              // opt compilation aborted for some reason.
              cm = baselineCompile(method);
            }
          }           
        }
      }
    }
    if (VM.LogAOSEvents) {
      VM_AOSLogging.recordCompileTime(cm, 0.0);
    }
    return cm;
    //-#endif
  }

  /**
   * Compile the stub for a native method when it is first invoked.
   * @param method the method to compile
   * @return its compiled method.
   */
  public static VM_CompiledMethod compile(VM_NativeMethod method) {
    VM_Callbacks.notifyMethodCompile(method, VM_CompiledMethod.JNI);
    long start = 0;
    if (VM.MeasureCompilation || VM.BuildForAdaptiveSystem) {
      start = VM_Thread.getCurrentThread().accumulateCycles();
    }

    VM_CompiledMethod cm = com.ibm.JikesRVM.jni.VM_JNICompiler.compile(method);
    if (VM.verboseJNI) {
      VM.sysWriteln("[Dynamic-linking native method " + 
                    method.getDeclaringClass() + "." + method.getName() +
                    " "+method.getDescriptor());
    }

    if (VM.MeasureCompilation || VM.BuildForAdaptiveSystem) {
      long end = VM_Thread.getCurrentThread().accumulateCycles();
      double compileTime = VM_Time.cyclesToMillis(end - start);
      cm.setCompilationTime(compileTime);
      record(JNI_COMPILER, method, cm);
    }
    
    return cm;
  }

  /**
   * returns the string version of compiler number, using the naming scheme
   * in this file
   * @param compiler the compiler of interest
   * @return the string version of compiler number
   */
  public static String getCompilerName(byte compiler) {
    return name[compiler];
  }

}
