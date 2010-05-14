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
package org.mmtk.harness.lang.ast;

import org.mmtk.harness.lang.Visitor;
import org.mmtk.harness.lang.parser.Token;

/**
 * AST node for the general alloc(refs,nonrefs,align) allocation method
 */
public class Alloc extends AbstractAST implements Expression {
  /** Call site ID */
  private final int site;
  /** Number of reference fields */
  private final Expression refCount;
  /** Number of data fields */
  private final Expression dataCount;
  /** Double align the object? */
  private final Expression doubleAlign;

  /**
   * Allocate an object.
   * @param t The parser token for this node
   * @param site A unique site ID
   * @param refCount Integer expression - number of reference fields
   * @param dataCount Integer expression - number of data fields
   * @param doubleAlign Boolean expression - whether to 8-byte align
   */
  public Alloc(Token t, int site, Expression refCount, Expression dataCount, Expression doubleAlign) {
    super(t);
    this.site = site;
    this.refCount = refCount;
    this.dataCount = dataCount;
    this.doubleAlign = doubleAlign;
  }

  /** @see org.mmtk.harness.lang.ast.AbstractAST#accept(org.mmtk.harness.lang.Visitor) */
  @Override
  public Object accept(Visitor v) {
    return v.visit(this);
  }

  /**
   * @return The allocation site number
   */
  public int getSite() { return site; }
  /** @return refCount */
  public Expression getRefCount() { return refCount; }
  /** @return dataCount */
  public Expression getDataCount() { return dataCount; }
  /** @return doubleAlign */
  public Expression getDoubleAlign() { return doubleAlign; }
}
