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
package gnu.java.lang.management;

import org.jikesrvm.JMXSupport;

public final class VMRuntimeMXBeanImpl {

  static String[] getInputArguments() {
    return JMXSupport.getInputArguments();
  }

  static String getName() {
    return JMXSupport.getName();
  }

  static long getStartTime() {
    return JMXSupport.getStartTime();
  }

}
