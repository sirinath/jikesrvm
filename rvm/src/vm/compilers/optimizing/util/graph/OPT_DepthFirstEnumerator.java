/*
 * This file is part of the Jikes RVM project (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
//$Id$
package com.ibm.JikesRVM.opt;

import  java.util.Enumeration;
import  java.util.NoSuchElementException;

/**
 * @author Mauricio J. Serrano
 * @author John Whaley
 */
final class OPT_DepthFirstEnumerator
    implements Enumeration {
  OPT_Stack stack;
  int mark;

  OPT_DepthFirstEnumerator (OPT_GraphNode start, int markNumber) {
    stack = new OPT_Stack();
    stack.push(start);
    mark = markNumber;
  }

  public boolean hasMoreElements () {
    if (stack == null)
      return  false;
    /*Enumeration*/

    OPT_LinkedListObjectEnumerator e = stack.elements();
    while (e.hasMoreElements()) {
      OPT_GraphNode node = (OPT_GraphNode)e.next
      /*Element*/
      ();
      if (node.getScratch() != mark)
        return  true;
    }
    return  false;
  }

  public Object nextElement () {
    return  next();
  }

  public OPT_GraphNode next () {
    if (stack == null)
      throw  new NoSuchElementException("OPT_DepthFirstEnumerator");
    while (!stack.isEmpty()) {
      OPT_GraphNode node = (OPT_GraphNode)stack.pop();
      if (node.getScratch() != mark) {
        for (Enumeration e = node.outNodes(); e.hasMoreElements();) {
          OPT_GraphNode n = (OPT_GraphNode)e.nextElement();
          if (n != null)
            stack.push(n);
        }
        node.setScratch(mark);
        return  node;
      }
    }
    throw  new NoSuchElementException("OPT_DepthFirstEnumerator");
  }

  private OPT_DepthFirstEnumerator () {
  }
  public static OPT_DepthFirstEnumerator EMPTY = new OPT_DepthFirstEnumerator();
}
