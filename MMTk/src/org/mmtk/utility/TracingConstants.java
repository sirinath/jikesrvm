/**
 * This file is part of MMTk (http://jikesrvm.sourceforge.net).
 * MMTk is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Department of Computer Science,
 *     University of Massachusetts, Amherst. 2003.
 */

package org.mmtk.utility;

import org.vmmagic.unboxed.*;

/**
 * The constants needed when storing events and then generating the trace.
 * 
 * $Id$
 * 
 * @author <a href="http://www-ali.cs.umass.edu/~hertz">Matthew Hertz</a>
 * @version $Revision$
 * @date $Date$
 */
public interface TracingConstants {
  Word TRACE_EXACT_ALLOC = Word.zero();
  Word TRACE_BOOT_ALLOC = Word.one().lsh(0);
  Word TRACE_ALLOC = Word.one().lsh(1);
  Word TRACE_DEATH = Word.one().lsh(2);
  Word TRACE_FIELD_SET = Word.one().lsh(3);
  Word TRACE_ARRAY_SET = Word.one().lsh(4);
  Word TRACE_TIB_SET = Word.one().lsh(5);
  Word TRACE_STATIC_SET = Word.one().lsh(6);
  Word TRACE_BOOTSTART = Word.one().lsh(7);
  Word TRACE_BOOTEND = Word.one().lsh(8);
  Word TRACE_GCSTART = Word.one().lsh(9);
  Word TRACE_GCEND = Word.one().lsh(10);
  Word TRACE_GCROOT = Word.one().lsh(11);
  Word TRACE_GCBAR = Word.one().lsh(12);
  Word TRACE_THREAD_SWITCH = Word.one().lsh(13);
  Word TRACE_STACKDELTA = Word.one().lsh(14);
  Word TRACE_ROOTPTR = Word.one().lsh(15);
  Word TRACE_EXACT_IMMORTAL_ALLOC = Word.one().lsh(16);
  Word TRACE_IMMORTAL_ALLOC = Word.one().lsh(17);
}

