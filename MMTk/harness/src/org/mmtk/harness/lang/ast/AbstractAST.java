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

import org.mmtk.harness.lang.parser.Token;

/**
 * Abstract parent of all the components of an AST
 */
public abstract class  AbstractAST implements AST {

  /** Source code line corresponding to this syntax element */
  private final int line;
  /** Source code column corresponding to this syntax element */
  private final int column;

  protected AbstractAST(Token t) {
    this(t.beginLine, t.beginColumn);
  }

  protected AbstractAST(int line, int column) {
    this.line = line;
    this.column = column;
  }

  @Override
  public int getLine() {
    return line;
  }

  @Override
  public int getColumn() {
    return column;
  }
}
