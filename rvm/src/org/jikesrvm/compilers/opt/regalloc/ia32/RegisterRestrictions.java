/*
 *  This file is part of the Jikes RVM project (http://jikesrvm.org).
 *
 *  This file is licensed to You under the Eclipse Public License (EPL);
 *  You may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *      http://www.opensource.org/licenses/eclipse-1.0.php
 *
 *  See the COPYRIGHT.txt file distributed with this work for information
 *  regarding copyright ownership.
 */
package org.jikesrvm.compilers.opt.regalloc.ia32;

import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ADDSD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ADDSS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ANDNPD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ANDNPS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ANDPD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ANDPS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_BT_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMOV_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPEQSD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPEQSS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPLESD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPLESS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPLTSD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPLTSS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPNESD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPNESS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPNLESD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPNLESS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPNLTSD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPNLTSS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPORDSD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPORDSS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPUNORDSD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CMPUNORDSS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CVTSD2SI_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CVTSD2SS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CVTSI2SD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CVTSI2SS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CVTSS2SD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CVTSS2SI_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CVTTSD2SI_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_CVTTSS2SI_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_DIVSD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_DIVSS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FCLEAR;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FCMOV_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FCOMIP_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FCOMI_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_FNINIT;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_IMUL2_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVSX__B_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVSX__W_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVZX__B_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MOVZX__W_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MULSD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_MULSS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ORPD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_ORPS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_PREFETCHNTA_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SET__B_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SHLD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SHRD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SQRTSD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SQRTSS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SUBSD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_SUBSS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_TEST_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_UCOMISD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_UCOMISS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_XORPD_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IA32_XORPS_opcode;
import static org.jikesrvm.compilers.opt.ir.Operators.IR_PROLOGUE;
import static org.jikesrvm.compilers.opt.ir.Operators.MIR_LOWTABLESWITCH_opcode;

import java.util.ArrayList;
import java.util.Enumeration;

import org.jikesrvm.ArchitectureSpecificOpt.PhysicalRegisterSet;
import org.jikesrvm.VM;
import org.jikesrvm.compilers.opt.ir.BasicBlock;
import org.jikesrvm.compilers.opt.ir.Instruction;
import org.jikesrvm.compilers.opt.ir.MIR_BinaryAcc;
import org.jikesrvm.compilers.opt.ir.MIR_CacheOp;
import org.jikesrvm.compilers.opt.ir.MIR_Compare;
import org.jikesrvm.compilers.opt.ir.MIR_CondMove;
import org.jikesrvm.compilers.opt.ir.MIR_DoubleShift;
import org.jikesrvm.compilers.opt.ir.MIR_LowTableSwitch;
import org.jikesrvm.compilers.opt.ir.MIR_Move;
import org.jikesrvm.compilers.opt.ir.MIR_Set;
import org.jikesrvm.compilers.opt.ir.MIR_Test;
import org.jikesrvm.compilers.opt.ir.MIR_Unary;
import org.jikesrvm.compilers.opt.ir.MIR_UnaryNoRes;
import org.jikesrvm.compilers.opt.ir.Register;
import org.jikesrvm.compilers.opt.ir.operand.MemoryOperand;
import org.jikesrvm.compilers.opt.ir.operand.Operand;
import org.jikesrvm.compilers.opt.ir.operand.RegisterOperand;
import org.jikesrvm.compilers.opt.ir.operand.ia32.BURSManagedFPROperand;
import org.jikesrvm.compilers.opt.regalloc.GenericRegisterRestrictions;
import org.jikesrvm.compilers.opt.regalloc.LiveIntervalElement;

/**
 * An instance of this class encapsulates restrictions on register
 * assignment.
 */
public class RegisterRestrictions extends GenericRegisterRestrictions
    implements PhysicalRegisterConstants {

  /**
   * Allow scratch registers in PEIs?
   */
  public static final boolean SCRATCH_IN_PEI = true;

  protected RegisterRestrictions(PhysicalRegisterSet phys) {
    super(phys);
  }

  @Override
  public void addArchRestrictions(BasicBlock bb, ArrayList<LiveIntervalElement> symbolics) {
    // If there are any registers used in catch blocks, we want to ensure
    // that these registers are not used or evicted from scratch registers
    // at a relevant PEI, so that the assumptions of register homes in the
    // catch block remain valid.  For now, we do this by forcing any
    // register used in such a PEI as not spilled.  TODO: relax this
    // restriction for better code.
    for (Enumeration<Instruction> ie = bb.forwardInstrEnumerator(); ie.hasMoreElements();) {
      Instruction s = ie.nextElement();
      if (s.isPEI() && s.operator() != IR_PROLOGUE) {
        if (bb.hasApplicableExceptionalOut(s) || !SCRATCH_IN_PEI) {
          for (Enumeration<Operand> e = s.getOperands(); e.hasMoreElements();) {
            Operand op = e.nextElement();
            if (op != null && op.isRegister()) {
              noteMustNotSpill(op.asRegister().getRegister());
              handle8BitRestrictions(s);
            }
          }
        }
      }

      // handle special cases
      switch (s.getOpcode()) {
        case MIR_LOWTABLESWITCH_opcode: {
          RegisterOperand op = MIR_LowTableSwitch.getMethodStart(s);
          noteMustNotSpill(op.getRegister());
          op = MIR_LowTableSwitch.getIndex(s);
          noteMustNotSpill(op.getRegister());
        }
        break;
        case IA32_MOVZX__B_opcode:
        case IA32_MOVSX__B_opcode: {
          if (MIR_Unary.getVal(s).isRegister()) {
            RegisterOperand val = MIR_Unary.getVal(s).asRegister();
            restrictTo8Bits(val.getRegister());
          }
        }
        break;
        case IA32_SET__B_opcode: {
          if (MIR_Set.getResult(s).isRegister()) {
            RegisterOperand op = MIR_Set.getResult(s).asRegister();
            restrictTo8Bits(op.getRegister());
          }
        }
        break;

        default:
          handle8BitRestrictions(s);
          break;
      }
    }
    for (Enumeration<Instruction> ie = bb.forwardInstrEnumerator(); ie.hasMoreElements();) {
      Instruction s = ie.nextElement();
      if (s.operator() == IA32_FNINIT) {
        // No floating point register survives across an FNINIT
        for (LiveIntervalElement symb : symbolics) {
          if (symb.getRegister().isFloatingPoint()) {
            if (contains(symb, regAllocState.getDFN(s))) {
              addRestrictions(symb.getRegister(), phys.getFPRs());
            }
          }
        }
      } else if (s.operator() == IA32_FCLEAR) {
        // Only some FPRs survive across an FCLEAR
        for (LiveIntervalElement symb : symbolics) {
          if (symb.getRegister().isFloatingPoint()) {
            if (contains(symb, regAllocState.getDFN(s))) {
              int nSave = MIR_UnaryNoRes.getVal(s).asIntConstant().value;
              for (int i = nSave; i < NUM_FPRS; i++) {
                addRestriction(symb.getRegister(), phys.getFPR(i));
              }
            }
          }
        }
      }
    }
  }

  /**
   * @param s the instruction to check
   * @return {@code true} if and only if the instruction contains an 8-bit memory operand
   */
  final boolean has8BitMemoryOperand(Instruction s) {
    for (Enumeration<Operand> me = s.getMemoryOperands(); me.hasMoreElements();) {
      MemoryOperand mop = (MemoryOperand) me.nextElement();
      if (mop.size == 1) {
        return true;
      }
    }
    return false;
  }

  /**
   * Ensure that if an operand has an 8 bit memory operand that
   * all of its register operands are in 8 bit registers.
   * @param s the instruction to restrict
   */
  final void handle8BitRestrictions(Instruction s) {
    for (Enumeration<Operand> me = s.getMemoryOperands(); me.hasMoreElements();) {
      MemoryOperand mop = (MemoryOperand) me.nextElement();
      if (mop.size == 1) {
        for (Enumeration<Operand> e2 = s.getRootOperands(); e2.hasMoreElements();) {
          Operand rootOp = e2.nextElement();
          if (rootOp.isRegister()) {
            restrictTo8Bits(rootOp.asRegister().getRegister());
          }
        }
      }
    }
  }

  /**
   * Ensures that a particular register is only assigned to AL, BL, CL, or
   * DL, since these are the only 8-bit registers we normally address.
   *
   * @param r the register that needs to be restricted to 8 bits
   */
  final void restrictTo8Bits(Register r) {
    Register ESP = phys.getESP();
    Register EBP = phys.getEBP();
    Register ESI = phys.getESI();
    Register EDI = phys.getEDI();
    addRestriction(r, ESP);
    addRestriction(r, EBP);
    addRestriction(r, ESI);
    addRestriction(r, EDI);
  }

  /**
   * Given symbolic register r that appears in instruction s, does the
   * architecture demand that r be assigned to a physical register in s?
   *
   * @param r a symbolic register
   * @param s instruction where the register appears
   *
   * @return {@code true} if the symbolic register r must use a physical
   *  register in the instruction, {@code false} if we can use a spill location
   */
  public static boolean mustBeInRegister(Register r, Instruction s) {
    switch (s.getOpcode()) {
      case IA32_PREFETCHNTA_opcode: {
        RegisterOperand op = MIR_CacheOp.getAddress(s).asRegister();
        if (op.getRegister() == r) return true;
      }
      break;

      case IA32_SQRTSS_opcode:
      case IA32_SQRTSD_opcode:
      case IA32_CVTSD2SI_opcode:
      case IA32_CVTSD2SS_opcode:
      case IA32_CVTSI2SD_opcode:
      case IA32_CVTSS2SD_opcode:
      case IA32_CVTSS2SI_opcode:
      case IA32_CVTTSD2SI_opcode:
      case IA32_CVTTSS2SI_opcode:
      case IA32_CVTSI2SS_opcode: {
        RegisterOperand op = MIR_Unary.getResult(s).asRegister();
        if (op.getRegister() == r) return true;
      }
      break;

      // Instructions that require 16byte alignment (not guaranteed by our
      // spills) must be forced to always use registers
      case IA32_ANDPS_opcode:
      case IA32_ANDNPS_opcode:
      case IA32_ORPS_opcode:
      case IA32_XORPS_opcode:
      case IA32_ANDPD_opcode:
      case IA32_ANDNPD_opcode:
      case IA32_ORPD_opcode:
      case IA32_XORPD_opcode:
        return true;

      case IA32_ADDSS_opcode:
      case IA32_CMPEQSS_opcode:
      case IA32_CMPLTSS_opcode:
      case IA32_CMPLESS_opcode:
      case IA32_CMPUNORDSS_opcode:
      case IA32_CMPNESS_opcode:
      case IA32_CMPNLTSS_opcode:
      case IA32_CMPNLESS_opcode:
      case IA32_CMPORDSS_opcode:
      case IA32_DIVSS_opcode:
      case IA32_MULSS_opcode:
      case IA32_SUBSS_opcode:
      case IA32_ADDSD_opcode:
      case IA32_CMPEQSD_opcode:
      case IA32_CMPLTSD_opcode:
      case IA32_CMPLESD_opcode:
      case IA32_CMPUNORDSD_opcode:
      case IA32_CMPNESD_opcode:
      case IA32_CMPNLTSD_opcode:
      case IA32_CMPNLESD_opcode:
      case IA32_CMPORDSD_opcode:
      case IA32_DIVSD_opcode:
      case IA32_MULSD_opcode:
      case IA32_SUBSD_opcode: {
        RegisterOperand op = MIR_BinaryAcc.getResult(s).asRegister();
        if (op.getRegister() == r) return true;
      }
      break;

      case IA32_UCOMISD_opcode:
      case IA32_UCOMISS_opcode: {
        RegisterOperand op = MIR_Compare.getVal1(s).asRegister();
        if (op.getRegister() == r) return true;
      }
      break;

      case IA32_SHRD_opcode:
      case IA32_SHLD_opcode: {
        RegisterOperand op = MIR_DoubleShift.getSource(s);
        if (op.getRegister() == r) return true;
      }
      break;
      case IA32_FCOMI_opcode:
      case IA32_FCOMIP_opcode: {
        Operand op = MIR_Compare.getVal2(s);
        if (!(op instanceof BURSManagedFPROperand)) {
          if (op.asRegister().getRegister() == r) return true;
        }
      }
      break;
      case IA32_IMUL2_opcode: {
        RegisterOperand op = MIR_BinaryAcc.getResult(s).asRegister();
        if (op.getRegister() == r) return true;
      }
      break;
      case MIR_LOWTABLESWITCH_opcode: {
        RegisterOperand op = MIR_LowTableSwitch.getIndex(s);
        if (op.getRegister() == r) return true;
      }
      break;
      case IA32_CMOV_opcode:
      case IA32_FCMOV_opcode: {
        RegisterOperand op = MIR_CondMove.getResult(s).asRegister();
        if (op.getRegister() == r) return true;
      }
      break;
      case IA32_MOVD_opcode: {
        RegisterOperand res = MIR_Move.getResult(s).asRegister();
        if (!res.isFloat() && !res.isDouble()) {
          // result is integer so source must be MM/XMM register and
          // result must remain a register
          if (VM.VerifyAssertions) {
            Operand val = MIR_Move.getValue(s);
            VM._assert(val.isRegister() && (val.isFloat() || val.isDouble()));
          }
          return true;
        }
        Operand val = MIR_Move.getValue(s);
        if (!val.isFloat() && !val.isDouble()) {
          // source is integer so destination must be MM/XMM register and
          // source must remain a register
          if (VM.VerifyAssertions) {
            VM._assert(res.isRegister() && (res.isFloat() || res.isDouble()));
          }
          return true;
        }
      }
      break;
      case IA32_MOVZX__B_opcode:
      case IA32_MOVSX__B_opcode: {
        RegisterOperand op = MIR_Unary.getResult(s).asRegister();
        if (op.getRegister() == r) return true;
      }
      break;
      case IA32_MOVZX__W_opcode:
      case IA32_MOVSX__W_opcode: {
        RegisterOperand op = MIR_Unary.getResult(s).asRegister();
        if (op.getRegister() == r) return true;
      }
      break;
      case IA32_SET__B_opcode: {
        if (MIR_Set.getResult(s).isRegister()) {
          RegisterOperand op = MIR_Set.getResult(s).asRegister();
          if (op.asRegister().getRegister() == r) return true;
        }
      }
      break;
      case IA32_TEST_opcode: {
        // at least 1 of the two operands must be in a register
        if (!MIR_Test.getVal2(s).isConstant()) {
          if (MIR_Test.getVal1(s).isRegister()) {
            if (MIR_Test.getVal1(s).asRegister().getRegister() == r) return true;
          } else if (MIR_Test.getVal2(s).isRegister()) {
            if (MIR_Test.getVal2(s).asRegister().getRegister() == r) return true;
          }
        }
      }
      break;
      case IA32_BT_opcode: {
        // val2 of bit test must be either a constant or register
        if (!MIR_Test.getVal2(s).isConstant()) {
          if (MIR_Test.getVal2(s).isRegister()) {
            if (MIR_Test.getVal2(s).asRegister().getRegister() == r) return true;
          }
        }
      }
      break;

      default:
        break;
    }
    return false;
  }

  /**
   * @param r the register to check
   * @return {@code true} if the physical register r hold an 8-bit value?
   */
  private boolean okFor8(Register r) {
    Register ESP = phys.getESP();
    Register EBP = phys.getEBP();
    Register ESI = phys.getESI();
    Register EDI = phys.getEDI();
    return (r != ESP && r != EBP && r != ESI && r != EDI);
  }

  @Override
  public boolean isForbidden(Register symb, Register r, Instruction s) {

    // Look at 8-bit restrictions.
    switch (s.getOpcode()) {
      case IA32_MOVZX__B_opcode:
      case IA32_MOVSX__B_opcode: {
        if (MIR_Unary.getVal(s).isRegister()) {
          RegisterOperand val = MIR_Unary.getVal(s).asRegister();
          if (val.getRegister() == symb) {
            return !okFor8(r);
          }
        }
      }
      break;
      case IA32_SET__B_opcode: {
        if (MIR_Set.getResult(s).isRegister()) {
          RegisterOperand op = MIR_Set.getResult(s).asRegister();
          if (op.asRegister().getRegister() == symb) {
            return !okFor8(r);
          }
        }
      }
      break;
    }

    if (has8BitMemoryOperand(s)) {
      return !okFor8(r);
    }

    // Otherwise, it's OK.
    return false;
  }
}
