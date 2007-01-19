/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2006
 */
package com.ibm.jikesrvm.ia32.opt.ir;

import com.ibm.jikesrvm.ArchitectureSpecific.OPT_PhysicalRegisterSet;

import com.ibm.jikesrvm.VM;
import com.ibm.jikesrvm.classloader.VM_TypeReference;
import com.ibm.jikesrvm.opt.OPT_LiveIntervalElement;
import com.ibm.jikesrvm.opt.OPT_OptimizingCompilerException;
import com.ibm.jikesrvm.opt.ir.Empty;
import com.ibm.jikesrvm.opt.ir.MIR_CondBranch;
import com.ibm.jikesrvm.opt.ir.MIR_CondBranch2;
import com.ibm.jikesrvm.opt.ir.MIR_Move;
import com.ibm.jikesrvm.opt.ir.OPT_BasicBlock;
import com.ibm.jikesrvm.opt.ir.OPT_BasicBlockEnumeration;
import com.ibm.jikesrvm.opt.ir.OPT_IR;
import com.ibm.jikesrvm.opt.ir.OPT_Instruction;
import com.ibm.jikesrvm.opt.ir.OPT_InstructionEnumeration;
import com.ibm.jikesrvm.opt.ir.OPT_MachineSpecificIR;
import com.ibm.jikesrvm.opt.ir.OPT_Operand;
import com.ibm.jikesrvm.opt.ir.OPT_OperandEnumeration;
import com.ibm.jikesrvm.opt.ir.OPT_Operator;
import com.ibm.jikesrvm.opt.ir.OPT_Register;
import com.ibm.jikesrvm.opt.ir.OPT_RegisterOperand;

import static com.ibm.jikesrvm.opt.ir.OPT_Operators.*;

/**
 * Wrappers around IA32-specific IR common to both 32 & 64 bit
 * 
 * $Id: OPT_IA32ConditionOperand.java 10996 2006-11-16 23:37:12Z dgrove-oss $
 * 
 * @author Steve Blackburn
 */
public abstract class OPT_MachineSpecificIRIA extends OPT_MachineSpecificIR {

  /**
   * Wrappers around IA32-specific IR (32-bit specific)
   */
  public static final class IA32 extends OPT_MachineSpecificIRIA {
    public static final IA32 singleton = new IA32();
    
    /* common to all ISAs */
    @Override
    public boolean mayEscapeThread(OPT_Instruction instruction) {
      switch (instruction.getOpcode()) {
      case PREFETCH_opcode:
        return false;
      case GET_JTOC_opcode: case GET_CURRENT_PROCESSOR_opcode:
        return true;
      default:
        throw  new OPT_OptimizingCompilerException("OPT_SimpleEscapge: Unexpected " + instruction);
      }
    }
    @Override
    public boolean mayEscapeMethod(OPT_Instruction instruction) {
      return mayEscapeThread(instruction); // at this stage we're no more specific
    }
  }
  
  /**
   * Wrappers around EMT64-specific IR (64-bit specific)
   */
  public static final class EM64T extends OPT_MachineSpecificIRIA {
    public static final EM64T singleton = new EM64T();

    /* common to all ISAs */
    @Override
    public boolean mayEscapeThread(OPT_Instruction instruction) {
      switch (instruction.getOpcode()) {
      case PREFETCH_opcode:
        return false;
      case GET_JTOC_opcode: case GET_CURRENT_PROCESSOR_opcode:
      case LONG_OR_opcode: case LONG_AND_opcode: case LONG_XOR_opcode:
      case LONG_SUB_opcode:case LONG_SHL_opcode: case LONG_ADD_opcode:
      case LONG_SHR_opcode:case LONG_USHR_opcode:case LONG_NEG_opcode:
      case LONG_MOVE_opcode: case LONG_2ADDR_opcode:
        return true;
      default:
        throw  new OPT_OptimizingCompilerException("OPT_SimpleEscapge: Unexpected " + instruction);
      }
    }
    @Override
    public boolean mayEscapeMethod(OPT_Instruction instruction) {
      return mayEscapeThread(instruction); // at this stage we're no more specific
    } 
  }
 
  
  /* 
   * Generic (32/64 neutral) IA support
   */
  
  /* common to all ISAs */
  @Override
  public boolean isConditionOperand(OPT_Operand operand) {
    return operand instanceof OPT_IA32ConditionOperand;
  }
  @Override
  public void mutateMIRCondBranch(OPT_Instruction cb) {
    MIR_CondBranch.mutate(cb, IA32_JCC,
        MIR_CondBranch2.getCond1(cb), 
        MIR_CondBranch2.getTarget1(cb),
        MIR_CondBranch2.getBranchProfile1(cb));
  }
  @Override
  public boolean isHandledByRegisterUnknown(char opcode) {
    return (opcode == PREFETCH_opcode);
  }

  /* unique to IA */
  @Override
  public boolean isAdviseESP(OPT_Operator operator) {
    return operator == ADVISE_ESP; 
  }
  @Override
  public boolean isFClear(OPT_Operator operator) {
    return operator == IA32_FCLEAR; 
  }
  @Override
  public boolean isFNInit(OPT_Operator operator) {
    return operator == IA32_FNINIT; 
  }
  
  @Override
  public boolean isBURSManagedFPROperand(OPT_Operand operand) {
    return operand instanceof OPT_BURSManagedFPROperand; 
  }
  @Override
  public int getBURSManagedFPRValue(OPT_Operand operand) {
    return Integer.valueOf(((OPT_BURSManagedFPROperand)operand).regNum);
  }
  
  /**
   * Mutate FMOVs that end live ranges
   * 
   * @param live The live interval for a basic block/reg pair
   * @param register The register for this live interval
   * @param dfnbegin The (adjusted) begin for this interval
   * @param dfnend The (adjusted) end for this interval
   */
  @Override
  public boolean mutateFMOVs(OPT_LiveIntervalElement live, OPT_Register register,
      int dfnbegin, int dfnend) {
    OPT_Instruction end = live.getEnd();
    if (end != null && end.operator == IA32_FMOV) {
      if (dfnend == dfnbegin) {
        // if end, an FMOV, both begins and ends the live range,
        // then end is dead.  Change it to a NOP and return null. 
        Empty.mutate(end, NOP);
        return false;
      } else {
        if (!end.isPEI()) {
          if (VM.VerifyAssertions) {                    
            OPT_Operand value = MIR_Move.getValue(end);
            VM._assert(value.isRegister());
            VM._assert(MIR_Move.getValue(end).asRegister().register == register);
          }
          end.operator = IA32_FMOV_ENDING_LIVE_RANGE;
        }
      }
    }
    return true;
  }
  
  /**
   *  Rewrite floating point registers to reflect changes in stack
   *  height induced by BURS. 
   * 
   *  Side effect: update the fpStackHeight in MIRInfo
   */
  public void rewriteFPStack(OPT_IR ir) {
    OPT_PhysicalRegisterSet phys = ir.regpool.getPhysicalRegisterSet();
    for (OPT_BasicBlockEnumeration b = ir.getBasicBlocks(); b.hasMoreElements(); ) {
      OPT_BasicBlock bb = b.nextElement();

      // The following holds the floating point stack offset from its
      // 'normal' position.
      int fpStackOffset = 0;

      for (OPT_InstructionEnumeration inst = bb.forwardInstrEnumerator(); 
           inst.hasMoreElements();) {
        OPT_Instruction s = inst.next();
        for (OPT_OperandEnumeration ops = s.getOperands(); 
             ops.hasMoreElements(); ) {
          OPT_Operand op = ops.next();
          if (op.isRegister()) {
            OPT_RegisterOperand rop = op.asRegister();
            OPT_Register r = rop.register;

            // Update MIR state for every phyiscal FPR we see
            if (r.isPhysical() && r.isFloatingPoint() &&
                s.operator() != DUMMY_DEF && 
                s.operator() != DUMMY_USE) {
              int n = OPT_PhysicalRegisterSet.getFPRIndex(r);
              if (fpStackOffset != 0) {
                n += fpStackOffset;
                rop.register = phys.getFPR(n);
              }
              ir.MIRInfo.fpStackHeight = 
                Math.max(ir.MIRInfo.fpStackHeight, n+1);
            }
          } else if (op instanceof OPT_BURSManagedFPROperand) {
            int regNum = ((OPT_BURSManagedFPROperand)op).regNum;
            s.replaceOperand(op, new OPT_RegisterOperand(phys.getFPR(regNum), 
                                                         VM_TypeReference.Double));
          }
        }
        // account for any effect s has on the floating point stack
        // position.
        if (s.operator().isFpPop()) {
          fpStackOffset--;
        } else if (s.operator().isFpPush()) {
          fpStackOffset++;
        }
        if (VM.VerifyAssertions) VM._assert(fpStackOffset >= 0);
      }
    }
  }
}
