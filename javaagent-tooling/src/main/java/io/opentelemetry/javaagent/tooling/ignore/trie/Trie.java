/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore.trie;

import org.checkerframework.checker.nullness.qual.Nullable;

/** A prefix tree that maps from the longest matching prefix to a value {@code V}. */
public interface Trie<V> {

  /** Start building a trie. */
  static <V> Builder<V> newBuilder() {
    return new TrieImpl.BuilderImpl<>();
  }

  /**
   * Returns the value associated with the longest matched prefix, or null if there wasn't a match.
   * For example: for a trie containing an {@code ("abc", 10)} entry {@code trie.getOrNull("abcd")}
   * will return {@code 10}.
   */
  @Nullable
  V getOrNull(CharSequence str);

  interface Builder<V> {

    /** Associate {@code value} with the string {@code str}. */
    Builder<V> put(CharSequence str, V value);

    Trie<V> build();
  }
}
