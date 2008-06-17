/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Common Public License (CPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/cpl1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.runtime;

import org.jikesrvm.VM;
import org.jikesrvm.classloader.RVMField;
import org.jikesrvm.classloader.NormalMethod;

/**
 * Entrypoints that are specific to instruction architecture.
 */
public interface ArchEntrypoints {
  NormalMethod newArrayArrayMethod =
      EntrypointHelper.getMethod("Lorg/jikesrvm/" + ArchEntrypoints.arch + "/MultianewarrayHelper;", "newArrayArray", "(IIII)Ljava/lang/Object;");
  String arch = VM.BuildForIA32 ? "ia32" : "ppc";
  String ArchCodeArrayName = "Lorg/jikesrvm/ArchitectureSpecific$CodeArray;";
  RVMField reflectiveMethodInvokerInstructionsField =
      EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/OutOfLineMachineCode;",
               "reflectiveMethodInvokerInstructions",
               ArchCodeArrayName);
  RVMField saveThreadStateInstructionsField =
      EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/OutOfLineMachineCode;", "saveThreadStateInstructions", ArchCodeArrayName);
  RVMField threadSwitchInstructionsField =
      EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/OutOfLineMachineCode;", "threadSwitchInstructions", ArchCodeArrayName);
  RVMField restoreHardwareExceptionStateInstructionsField =
      EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/OutOfLineMachineCode;",
               "restoreHardwareExceptionStateInstructions",
               ArchCodeArrayName);
  RVMField registersIPField =
      EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/Registers;", "ip", "Lorg/vmmagic/unboxed/Address;");
  RVMField registersFPRsField = EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/Registers;", "fprs", "[D");
  RVMField registersGPRsField =
      EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/Registers;", "gprs", "Lorg/vmmagic/unboxed/WordArray;");
  RVMField registersInUseField = EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/Registers;", "inuse", "Z");
  RVMField registersLRField =
      (VM.BuildForPowerPC) ? EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/Registers;",
                                      "lr",
                                      "Lorg/vmmagic/unboxed/Address;") : null;
  RVMField toSyncProcessorsField =
      (VM.BuildForPowerPC) ? EntrypointHelper.getField("Lorg/jikesrvm/scheduler/Scheduler;", "toSyncProcessors", "I") : null;
  RVMField registersFPField =
      (VM.BuildForIA32) ? EntrypointHelper.getField("Lorg/jikesrvm/" + arch + "/Registers;",
                                   "fp",
                                   "Lorg/vmmagic/unboxed/Address;") : null;
  RVMField framePointerField =
      (VM.BuildForIA32) ? EntrypointHelper.getField("Lorg/jikesrvm/scheduler/Processor;",
                                   "framePointer",
                                   "Lorg/vmmagic/unboxed/Address;") : null;
  RVMField hiddenSignatureIdField =
      (VM.BuildForIA32) ? EntrypointHelper.getField("Lorg/jikesrvm/scheduler/Processor;", "hiddenSignatureId", "I") : null;
  RVMField arrayIndexTrapParamField =
      (VM.BuildForIA32) ? EntrypointHelper.getField("Lorg/jikesrvm/scheduler/Processor;", "arrayIndexTrapParam", "I") : null;
  RVMField JNIEnvSavedJTOCField =
      (VM.BuildForPowerPC) ? EntrypointHelper.getField("Lorg/jikesrvm/jni/JNIEnvironment;",
                                      "savedJTOC",
                                      "Lorg/vmmagic/unboxed/Address;") : null;
}
