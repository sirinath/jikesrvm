/*
 * (C) Copyright IBM Corp. 2001
 */
//$Id$

import instructionFormats.*;
import java.util.*;

/**
 * Defines the context in which BC2IR will abstractly interpret
 * a method's bytecodes and populate targetIR with instructions.
 *
 * @author Dave Grove
 * @author Martin Trapp
 **/
final class OPT_GenerationContext implements OPT_Constants, 
					     OPT_Operators {

  //////////
  // These fields are used to communicate information from its 
  // caller to OPT_BC2IR
  //////////
  /**
   * The original method (root of the calling context tree)
   */
  VM_Method original_method;

  /**
   * The compiled method id assigned for this compilation of original_method
   */
  int original_cmid;

  /**
   * The method to be generated
   */
  VM_Method method;

  /**
   * The options to control the generation
   */
  OPT_Options options;

  /**
   * The CFG object into which instructions should be generated.
   */
  OPT_ControlFlowGraph cfg;

  /**
   * The register pool to be used during generation
   */
  OPT_RegisterPool temps;

  /**
   * The parameters which BC2IR should use to seed the local state
   * for the entry basic block.
   */
  OPT_Operand[] arguments;

  /**
   * The basic block into which BC2IR's caller will generate a "prologue."
   * BC2IR will add a CFG edge from prologue to the block containing the
   * instructions generated for bytecode 0, but it is its caller's
   * responsibility to populate the prologue with instructions.
   * All blocks generated by BC2IR will be injected by BC2IR.doFinalPass 
   * immediately
   * after prologue in the code ordering 
   * (ie prologue can assume it will fallthrough
   * to the first basic block in the ir generated for method.
   */
  OPT_BasicBlock prologue;

  /**
   * The basic block into which BC2IR's caller will generate an epilogue.
   * BC2IR will add CFG edges to this node, but it is its caller's
   * responsibility to populate it with instructions.
   * NOTE: After IR is generated one of two conditions will hold:
   * <ul>
   * <li> epilogue == cfg.lastInCodeOrder():  (if it is to be inlined, 
   *                                           then the generated cfg
   *                                           is expecting to "fallthrough" 
   *                                           to the next bblock)
   * <li> epilogue == null:  implies that there is no "normal" exit from 
   *                         the callee (all exits via throw)
   * </ul>
   * NOTE: BC2IR assumes that epilogue is a single basic block 
   *       (ie it has no out edges)
   */
  OPT_BasicBlock epilogue;

  /**
   * The exit node of the outermost CFG 
   * (only used by BC2IR for not-definitely caught athrows)
   */
  OPT_BasicBlock exit;

  /**
   * A catch, unlock, and rethrow exception handler used for 
   * synchronized methods.  
   */
  OPT_BasicBlock unlockAndRethrow;

  /**
   * The OPT_Register to which BC2IR should assign the return value(s)
   * of the method. It will be null when the method has a void return.
   */
  OPT_Register resultReg;

  /**
   * The enclosing exception handlers (null if there are none).
   */
  OPT_ExceptionHandlerBasicBlockBag enclosingHandlers;

  /**
   * Inlining context of the method to be generated
   */
  OPT_InlineSequence inlineSequence;

  /**
   * The OPT_InlineOracle to be consulted for all inlining decisions during
   * the generation of this IR.
   */
  OPT_InlineOracle inlinePlan;

  /**
   * Current estimate of the number of machine code instructions we're going 
   * to end up generating for this method.  
   * Only useful as part of inlining size heuristics, since it tends 
   * to be somewhat inaccurate.
   */
  int localMCSizeEstimate;

  /**
   * Current estimate of the number of machine code instructions we've 
   * generated in the parent context so far.
   * Only useful as part of inlining size heuristics, since it tends 
   * to be somewhat inaccurate.
   */
  int parentMCSizeEstimate;

  /**
   * Has semantic expansion been performed on the caller IR?
   * Used to defer inlining of semantic expansion targets until after se.
   */
  boolean semanticExpansionComplete;


  //////////
  // These fields are used to communicate information from BC2IR to its caller
  //////////
  /**
   * Did BC2IR generate a reachable exception handler while generating 
   * the IR for this method
   */
  boolean generatedExceptionHandlers;

  /**
   * Did BC2IR encounter a magic that requires us to allocate a stack frame?
   */
  boolean allocFrame;

  /**
   * Used to communicate the meet of the return values back to the caller
   * Mainly useful when BC2IR is doing inlining....allows the caller 
   * BC2IR object
   * to exploit knowledge the callee BC2IR object had about the result.
   */
  OPT_Operand result;

  //////////
  // Main public methods
  /////////

  /**
   * Use this constructor to create an outermost (non-inlined) 
   * OPT_GenerationContext.
   * 
   * @param meth the VM_Method whose IR will be generated
   * @param cmid the compiled method id to be used for this compilation
   * @param opts the OPT_Options to be used for the generation
   * @param ip the OPT_InlineOracle to be used for the generation
   * @param context the specialization context (null if none)
   */
  OPT_GenerationContext(VM_Method meth, 
			int cmid, 
			OPT_Options opts, 
			OPT_InlineOracle ip) {
    original_method = meth;
    original_cmid = cmid;
    method = meth;
    options = opts;
    inlinePlan = ip;
    inlineSequence = new OPT_InlineSequence(meth);

    // Create the CFG. Initially contains prologue, epilogue, and exit.
    cfg = new OPT_ControlFlowGraph(0);
    prologue = new OPT_BasicBlock(PROLOGUE_BCI, inlineSequence, cfg);
    epilogue = new OPT_BasicBlock(EPILOGUE_BCI, inlineSequence, cfg);
    cfg.addLastInCodeOrder(prologue);
    cfg.addLastInCodeOrder(epilogue);
    exit = cfg.exit();
    epilogue.insertOut(exit);

    // Create register pool, initialize arguments, resultReg.
    temps = new OPT_RegisterPool(meth);
    _ncGuards = new java.util.HashMap();
    initLocalPool();
    VM_Type[] params = meth.getParameterTypes();
    int numParams = params.length;
    int argIdx = 0;
    int localNum = 0;
    arguments = new OPT_Operand[method.isStatic()?numParams:numParams+1];
    // Insert IR_PROLOGUE instruction.  Loop below will fill in its operands
    OPT_Instruction prologueInstr = 
      Prologue.create(IR_PROLOGUE, arguments.length);
    appendInstruction(prologue, prologueInstr, PROLOGUE_BCI);

    if (!method.isStatic()) {
      VM_Type thisType = meth.getDeclaringClass();
      OPT_RegisterOperand thisOp = makeLocal(localNum, thisType);
      // The this param of a virtual method is by definition non null
      OPT_RegisterOperand guard = makeNullCheckGuard(thisOp.register);
      OPT_BC2IR.setGuard(thisOp, guard);
      appendInstruction(prologue,
			Move.create(GUARD_MOVE, guard.copyRO(), 
				    new OPT_TrueGuardOperand()),
			PROLOGUE_BCI);
      thisOp.setDeclaredType();
      thisOp.setExtant();
      arguments[0] = thisOp;
      Prologue.setFormal(prologueInstr, 0, thisOp.copyU2D());
      argIdx++; localNum++;
    }
    for (int paramIdx = 0; paramIdx < numParams; paramIdx++) {
      VM_Type argType = params[paramIdx];
      OPT_RegisterOperand argOp = makeLocal(localNum, argType);
      argOp.setDeclaredType();
      if (argType.isClassType()) {
	argOp.setExtant();
      }
      arguments[argIdx] = argOp;
      Prologue.setFormal(prologueInstr, argIdx, argOp.copyU2D());
      argIdx++; localNum++;
      if (argType.isLongType() || argType.isDoubleType()) {
	localNum++; // longs & doubles take two words of local space
      }
    }
    VM_Type returnType = meth.getReturnType();
    if (returnType != VM_Type.VoidType) {
      resultReg = temps.makeTemp(returnType).register;
    }
    
    enclosingHandlers = null;
    localMCSizeEstimate = VM_OptMethodSummary.inlinedSizeEstimate(method);

    completePrologue(true);
    completeEpilogue(true);
    completeExceptionHandlers(true);
  }

  /**
   * Create a child generation context from parent & callerBB to
   * generate IR for callsite. 
   * Make this 'static' to avoid confusing parent/child fields.
   *
   * @param parent the parent gc
   * @param ebag the enclosing exception handlers (null if none)
   * @param callee the callee method to be inlined 
   *        (may _not_ be equal to Call.getMethod(callSite).method)
   * @param callSite the Call instruction to be inlined.
   * @return the child context
   */
  static OPT_GenerationContext createChildContext(OPT_GenerationContext parent,
						  OPT_ExceptionHandlerBasicBlockBag ebag,
						  VM_Method callee,
						  OPT_Instruction callSite) {
    OPT_GenerationContext child = new OPT_GenerationContext();
    child.method = callee;
    child.original_method = parent.original_method;
    child.original_cmid = parent.original_cmid;

    // Some state gets directly copied to the child
    child.options = parent.options;
    child.temps = parent.temps;
    child._ncGuards = parent._ncGuards;
    child.exit = parent.exit;
    child.inlinePlan = parent.inlinePlan;
    child.localMCSizeEstimate = 
      VM_OptMethodSummary.inlinedSizeEstimate(child.method);
    child.parentMCSizeEstimate = parent.localMCSizeEstimate + 
      parent.parentMCSizeEstimate;
    child.semanticExpansionComplete = parent.semanticExpansionComplete;

    // Now inherit state based on callSite
    child.inlineSequence = new OPT_InlineSequence
      (child.method, callSite.position, callSite.bcIndex);
    child.enclosingHandlers = ebag;
    child.arguments = new OPT_Operand[Call.getNumberOfParams(callSite)];
    for (int i=0; i< child.arguments.length; i++) {
      child.arguments[i] = Call.getParam(callSite, i).copy(); // copy instead 
      // of clearing in case inlining aborts.
    }
    if (Call.hasResult(callSite)) {
      child.resultReg = Call.getResult(callSite).copyD2D().register;
      child.resultReg.setSpansBasicBlock(); // it will...
    }
 
    // Initialize the child CFG, prologue, and epilogue blocks
    child.cfg = new OPT_ControlFlowGraph(parent.cfg.numberOfNodes);
    child.prologue = new OPT_BasicBlock(PROLOGUE_BCI, 
					child.inlineSequence, child.cfg);
    child.prologue.exceptionHandlers = ebag;
    child.epilogue = new OPT_BasicBlock(EPILOGUE_BCI, 
					child.inlineSequence, child.cfg);
    child.epilogue.exceptionHandlers = ebag;
    child.cfg.addLastInCodeOrder(child.prologue);
    child.cfg.addLastInCodeOrder(child.epilogue);

    // Set up the local pool
    child.initLocalPool();

    // Insert moves from child.arguments to child's locals in prologue
    VM_Type[] params = child.method.getParameterTypes();
    int numParams = params.length;
    int argIdx = 0;
    int localNum = 0;
    if (!child.method.isStatic()) {
      OPT_Operand receiver = child.arguments[argIdx++];
      OPT_RegisterOperand local = null;
      if (receiver.isRegister()) {
	OPT_RegisterOperand objPtr = receiver.asRegister();
	if (OPT_ClassLoaderProxy.proxy.isAssignableWith(
							child.method.getDeclaringClass(), objPtr.type) != YES) {
	  // narrow type of actual to match formal static type implied by method
	  // VM.sysWrite("Narrowing reciever from "+objPtr+" to "
          // +child.method.getDeclaringClass());
	  objPtr.type = child.method.getDeclaringClass();
	  objPtr.clearPreciseType(); // Can be precise but not assignable 
	  // if enough classes aren't loaded
	  objPtr.setDeclaredType();
	}
	local = child.makeLocal(localNum++, objPtr);
	child.arguments[0] = local; // Avoid confusion in BC2IR of callee 
	// when objPtr is a local in the caller.
      } else if (receiver.isStringConstant()) {
	local = child.makeLocal(localNum++, VM_Type.JavaLangStringType);
	local.setPreciseType();
	// String constants trivially non-null
	OPT_RegisterOperand guard = child.makeNullCheckGuard(local.register);
	OPT_BC2IR.setGuard(local, guard);
	child.prologue.appendInstruction(Move.create(GUARD_MOVE, 
						     guard.copyRO(), 
						     new OPT_TrueGuardOperand()));
      } else {
	OPT_OptimizingCompilerException.UNREACHABLE("Unexpected receiver operand");
      }
      OPT_Instruction s = Move.create(REF_MOVE, local, receiver);
      s.bcIndex = PROLOGUE_BCI;
      s.position = callSite.position;
      child.prologue.appendInstruction(s);
    }
    for (int paramIdx = 0; paramIdx<numParams; paramIdx++, argIdx++) {
      VM_Type argType = params[paramIdx];
      OPT_RegisterOperand formal;
      OPT_Operand actual = child.arguments[argIdx];
      if (actual.isRegister()) {
	OPT_RegisterOperand rActual = actual.asRegister();
	if (OPT_ClassLoaderProxy.proxy.isAssignableWith(argType, rActual.type) 
            != YES) {
	  // narrow type of actual to match formal static type implied by method
	  // VM.sysWrite("Narrowing argument from "+objPtr+" to "+argType);
	  rActual.type = argType;
	  rActual.clearPreciseType(); // Can be precise but not 
	  // assignable if enough classes aren't loaded
	  rActual.setDeclaredType();
	}
	formal = child.makeLocal(localNum++, rActual);
	child.arguments[argIdx] = formal;  // Avoid confusion in BC2IR of 
	// callee when arg is a local in the caller.
      } else {
	formal = child.makeLocal(localNum++, argType);
      }
      OPT_Instruction s = 
	Move.create(OPT_IRTools.getMoveOp(argType), formal, actual);
      s.bcIndex = PROLOGUE_BCI;
      s.position = callSite.position;
      child.prologue.appendInstruction(s);
      if (argType.isLongType() || argType.isDoubleType()) {
	localNum++; // longs and doubles take two local words
      }
    }

    child.completePrologue(false);
    child.completeEpilogue(false);
    child.completeExceptionHandlers(false);

    return child;
  }

  /**
   * Only for internal use by OPT_Inliner (when inlining multiple targets)
   * This is probably not the prettiest way to handle this, but it requires
   * no changes to BC2IR's & OPT_Inliner's high level control logic.
   *
   * @param parent the parent gc
   * @param ebag the enclosing exception handlers (null if none)
   * @return the synthetic context
   */
  static OPT_GenerationContext createSynthetic(OPT_GenerationContext parent,
					       OPT_ExceptionHandlerBasicBlockBag ebag) {
    // Create the CFG. Initially contains prologue and epilogue
    OPT_GenerationContext child = new OPT_GenerationContext();

    child.cfg = new OPT_ControlFlowGraph(-100000);

    // It may be wrong to use the parent inline sequence as the
    // position here, but it seems to work out.  This is a synthetic
    // context that is just used as a container for multiple inlined
    // targets, so in the cases that I've observed where the prologue
    // and epilogue don't disappear, it was correct to have the
    // parent's position. -- Matt
    child.prologue = new OPT_BasicBlock(PROLOGUE_BCI, parent.inlineSequence, 
					parent.cfg);
    child.prologue.exceptionHandlers = ebag;
    child.epilogue = new OPT_BasicBlock(EPILOGUE_BCI, parent.inlineSequence, 
					parent.cfg);
    child.epilogue.exceptionHandlers = ebag;
    child.cfg.addLastInCodeOrder(child.prologue);
    child.cfg.addLastInCodeOrder(child.epilogue);

    // All other fields are intentionally left null.
    // We are only really using this context to transfer a synthetic CFG
    // from the low-level OPT_Inliner.execute back to its caller.
    // TODO: Rewrite OPT_GenerationContext to be a subclass of a root
    // class that is just a CFG wrapper.  Then, have an instance of this 
    // new parent
    // class be the return value for the main entrypoints in OPT_Inliner
    // and create an instance of the root class instead of OPT_GC when 
    // inlining multiple targets.

    return child;
  }


  /**
   * Use this to transfer state back from a child context back to its parent.
   * 
   * @param parent the parent context that will receive the state
   * @param child  the child context from which the state will be taken
   */
  public static void transferState(OPT_GenerationContext parent, 
				   OPT_GenerationContext child) {
    // Update parent's size estimate to reflect the inlining that we just 
    // committed (we subtract out the size of a call because
    // ee've replaced a call in the parent with the inlined body).
    parent.localMCSizeEstimate += 
      child.localMCSizeEstimate - VM_OptMethodSummary.CALL_COST;

    parent.cfg.numberOfNodes = child.cfg.numberOfNodes;
    if (child.generatedExceptionHandlers)
      parent.generatedExceptionHandlers = true;
    if (child.allocFrame)
      parent.allocFrame = true;
  }


  ///////////
  // Local variables
  ///////////

  // The registers to use for various types of locals.
  // Note that "int" really means 32-bit gpr and thus includes references.
  private OPT_Register[] intLocals;
  private OPT_Register[] floatLocals;
  private OPT_Register[] longLocals;
  private OPT_Register[] doubleLocals;

  private void initLocalPool() {
    int numLocals = method.getLocalWords();
    intLocals = new OPT_Register[numLocals];
    floatLocals = new OPT_Register[numLocals];
    longLocals = new OPT_Register[numLocals];
    doubleLocals = new OPT_Register[numLocals];
  }

  private OPT_Register[] getPool(VM_Type type) {
    if (type == VM_Type.FloatType) {
      return floatLocals;
    } else if (type == VM_Type.LongType) {
      return longLocals;
    } else if (type == VM_Type.DoubleType) {
      return doubleLocals;
    } else {
      return intLocals;
    }
  }


  /**
   * Return the OPT_Register used to for local i of VM_Type type
   */
  public OPT_Register localReg(int i, VM_Type type) {
    OPT_Register[] pool = getPool(type);
    if (pool[i] == null) {
      pool[i] = temps.getReg(type);
      pool[i].setLocal();
    }
    return pool[i];
  }

  /**
   * Make a register operand that refers to the given local variable number
   * and has the given type.
   *
   * @param i local variable number
   * @param type desired data type
   */
  public final OPT_RegisterOperand makeLocal(int i, VM_Type type) {
    return new OPT_RegisterOperand(localReg(i, type), type);
  }

  /**
   * Make a register operand that refers to the given local variable number,
   * and inherits its properties (type, flags) from props
   *
   * @param i local variable number
   * @param props OPT_RegisterOperand to inherit flags from
   */
  final OPT_RegisterOperand makeLocal(int i, OPT_RegisterOperand props) {
    OPT_RegisterOperand local = makeLocal(i, props.type);
    local.setInheritableFlags(props);
    OPT_BC2IR.setGuard(local, OPT_BC2IR.getGuard(props));
    return local;
  }

  /**
   * Get the local number for a given register 
   */
  public final int getLocalNumberFor(OPT_Register reg, VM_Type type) {
    OPT_Register[] pool = getPool(type);
    for (int i=0; i< pool.length; i++) {
      if (pool[i] == reg) return i;
    }
    return -1;
  }

  /**
   * Is the operand a particular bytecode local?
   */
  public final boolean isLocal(OPT_Operand op, int i, VM_Type type) {
    if (op instanceof OPT_RegisterOperand) {
      if (getPool(type)[i] == ((OPT_RegisterOperand)op).register) return true;
    }
    return false;
  }


  ///////////
  // Validation operands (guards)
  ///////////

  // For each register, we always use the same register as a validation operand.
  // This helps us avoid needlessly losing information at CFG join points.
  private java.util.HashMap _ncGuards;

  /**
   * Make a register operand to use as a null check guard for the 
   * given register.
   */
  OPT_RegisterOperand makeNullCheckGuard(OPT_Register ref) {
    OPT_RegisterOperand guard = (OPT_RegisterOperand)_ncGuards.get(ref);
    if (guard == null) {
      guard = temps.makeTempValidation();
      _ncGuards.put(ref, guard);
    }
    return guard;
  }


  ///////////
  // Implementation
  ///////////

  /**
   * for internal use only (in createInlinedContext)
   */
  private OPT_GenerationContext() {}


  /**
   * Fill in the rest of the method prologue.
   * PRECONDITION: arguments & temps have been setup/initialized.
   */
  private void completePrologue(boolean isOutermost) {
    // Deal with Uninteruptible code.
    if (!isOutermost && requiresUnintMarker()) {
      OPT_Instruction s = Empty.create(UNINT_BEGIN);
      appendInstruction(prologue, s, PROLOGUE_BCI);
    }

    // Deal with implicit monitorenter for synchronized methods.
    if (method.isSynchronized() && !options.MONITOR_NOP
    				&& !options.INVOKEE_THREAD_LOCAL) {
      OPT_Operand lockObject = getLockObject(PROLOGUE_BCI, prologue);
      OPT_Instruction s = MonitorOp.create(MONITORENTER, lockObject);
      appendInstruction(prologue, s, SYNCHRONIZED_MONITORENTER_BCI);
    }
  }


  /**
   * Fill in the rest of the method epilogue.
   * PRECONDITION: arguments & temps have been setup/initialized.
   */
  private void completeEpilogue(boolean isOutermost) {
    // Deal with implicit monitorexit for synchronized methods.
    if (method.isSynchronized() && !options.MONITOR_NOP 
    				&& !options.INVOKEE_THREAD_LOCAL) {
      OPT_Operand lockObject = getLockObject(EPILOGUE_BCI, epilogue);
      OPT_Instruction s = MonitorOp.create(MONITOREXIT, lockObject);
      appendInstruction(epilogue, s, SYNCHRONIZED_MONITOREXIT_BCI);
    }

    // Deal with Uninteruptible code.
    if (!isOutermost && requiresUnintMarker()) {
      OPT_Instruction s = Empty.create(UNINT_END);
      appendInstruction(epilogue, s, EPILOGUE_BCI);
    }

    if (isOutermost) {
      VM_Type returnType = method.getReturnType();
      OPT_Operand retVal = returnType.isVoidType() ? null : 
          new OPT_RegisterOperand(resultReg, returnType);
      OPT_Instruction s = Return.create(RETURN, retVal);
      appendInstruction(epilogue, s, EPILOGUE_BCI);
    }
  }


  /**
   * If the method is synchronized then we wrap it in a
   * synthetic exception handler that unlocks & rethrows
   * PRECONDITION: cfg, arguments & temps have been setup/initialized.
   */
  private void completeExceptionHandlers(boolean isOutermost) {
    if (method.isSynchronized() && !options.MONITOR_NOP) {
      OPT_ExceptionHandlerBasicBlock rethrow =
	      new OPT_ExceptionHandlerBasicBlock(SYNTH_CATCH_BCI, inlineSequence,
        new OPT_TypeOperand(VM_Type.JavaLangThrowableType), cfg);
      rethrow.exceptionHandlers = enclosingHandlers;
      OPT_RegisterOperand ceo = temps.makeTemp(VM_Type.JavaLangThrowableType);
      OPT_Instruction s = Nullary.create(GET_CAUGHT_EXCEPTION, ceo);
      appendInstruction(rethrow, s, SYNTH_CATCH_BCI);
      OPT_Operand lockObject = getLockObject(SYNTH_CATCH_BCI, rethrow);
      OPT_MethodOperand methodOp = OPT_MethodOperand.STATIC(OPT_Entrypoints.unlockAndThrow);
      methodOp.setIsNonReturningCall(true); // Used to keep cfg correct
      s = Call.create2(CALL, null, null, methodOp, lockObject, ceo);
      appendInstruction(rethrow, s, RUNTIME_SERVICES_BCI);
      cfg.insertBeforeInCodeOrder(epilogue, rethrow);

      // May be overly conservative 
      // (if enclosed by another catch of Throwable...)
      if (enclosingHandlers != null) {
	for (OPT_BasicBlockEnumeration e = enclosingHandlers.enumerator(); 
	     e.hasMoreElements();) {
	  OPT_BasicBlock eh = e.next();
	  rethrow.insertOut(eh);
	}
      }
      rethrow.setCanThrowExceptions();
      rethrow.setMayThrowUncaughtException();
      rethrow.insertOut(exit);

      // save a reference to this block so we can discard it if unused.
      unlockAndRethrow = rethrow;

      OPT_ExceptionHandlerBasicBlock[] sh = 
          new OPT_ExceptionHandlerBasicBlock[1];
      sh[0] = rethrow;
      enclosingHandlers = 
          new OPT_ExceptionHandlerBasicBlockBag(sh, enclosingHandlers);
      generatedExceptionHandlers = true;
    }
  }


  // Get either the class object or the this ptr...
  private OPT_Operand getLockObject(int bcIndex, OPT_BasicBlock target) {
    if (method.isStatic()) {
      // force java.lang.Class object into declaringClass.classForType
      method.getDeclaringClass().getClassForType();
      OPT_Instruction s = Unary.create(GET_CLASS_OBJECT,
				       temps.makeTemp(VM_Type.JavaLangObjectType),
				       new OPT_TypeOperand(method.getDeclaringClass()));
      appendInstruction(target, s, bcIndex);
      return Unary.getResult(s).copyD2U();
    } else {
      return makeLocal(0, arguments[0].getType());
    }
  }

  private void appendInstruction(OPT_BasicBlock b, 
				 OPT_Instruction s, 
				 int bcIndex) {
    s.position = inlineSequence;
    s.bcIndex = bcIndex;
    b.appendInstruction(s);
  }

  private boolean requiresUnintMarker() {
    if (method.getDeclaringClass().isInterruptible()) return false;
    
    // supress redundant markers by detecting when we're inlining
    // one Uninterruptible method into another one.
    for (OPT_InlineSequence p = inlineSequence.getCaller();
	 p != null;
	 p = p.getCaller()) {
      if (!p.getMethod().getDeclaringClass().isInterruptible()) return false;
    }

    return true;
  }

    
  /**
   * Make sure, the gc is still in sync with the IR, even if we applied some
   * optimizations. This method should be called before hir2lir conversions
   * which might trigger inlining.
   */
  void resync () {
    //make sure the _ncGuards contain no dangling mappings
    resync_ncGuards();
  }
  

  /**
   * This method makes sure that _ncGuard only maps to registers that
   * are actually in the IRs register pool.
   */
  private void resync_ncGuards ()
  {
    HashSet regPool = new HashSet();
    
    for (OPT_Register r = temps.getFirstRegister();
	 r != null;  r = r.next) regPool.add (r);
    
    Iterator i = _ncGuards.entrySet().iterator();
    while (i.hasNext()) {
      Map.Entry entry = (Map.Entry) i.next();
      if (!(regPool.contains (entry.getValue()))) i.remove();
    }
  }

  
  /**
   * Kill ncGuards, so we do not use outdated mappings unintendedly later on
   */
  void close () {
    _ncGuards = null;
  }
  

}
 
