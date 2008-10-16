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
package org.mmtk.harness.lang.ast;

import java.util.List;

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.MethodTable;
import org.mmtk.harness.lang.parser.Token;

/**
 * Create a new thread to call a method.
 */
public class Spawn extends AbstractAST implements Statement {
  /** Method table */
  private final MethodTable methods;
  /** Method name */
  private final String methodName;
  /** Parameter expressions */
  private final List<Expression> params;

  /**
   * Call a method.
   */
  public Spawn(Token t, MethodTable methods, String methodName, List<Expression> params) {
    super(t);
    this.methods = methods;
    this.methodName = methodName;
    this.params = params;
  }

  public void accept(Visitor v) {
    v.visit(this);
  }
  public List<Expression> getArgs() { return params; }
  public Method getMethod() { return methods.get(methodName); }
}
