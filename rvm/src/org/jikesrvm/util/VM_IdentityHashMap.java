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
package org.jikesrvm.util;

import org.jikesrvm.util.VM_HashMap.Bucket;

/**
 * The same as {@link VM_HashMap} except object identities determine equality
 * not the equals method.
 */
public final class VM_IdentityHashMap<K, V> extends VM_AbstractHashMap<K, V> {
  @Override
  boolean same(K k1, K k2) {
    return k1 == k2;
  }

  @Override
  AbstractBucket<K,V> createNewBucket(K key, V value, AbstractBucket<K, V> next) {
    return new Bucket<K,V>(key, value, next);
  }

  public VM_IdentityHashMap() {
    super(DEFAULT_SIZE);
  }

  public VM_IdentityHashMap(int size) {
    super(size);
  }
}
