/*
 * This file is part of Jikes RVM (http://jikesrvm.sourceforge.net).
 * The Jikes RVM project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright IBM Corp. 2001
 */
import java.io.ObjectOutputStream;

/**
 * @author unascribed
 */
class TestWrite
{
  public static void main(String args[]) {
    try {
      TestSerialization ts = new TestSerialization();
      ObjectOutputStream out = new ObjectOutputStream(System.out);
      out.writeObject(ts);
    } catch (java.io.IOException e) {
      e.printStackTrace(System.err);
    }
  }
}

