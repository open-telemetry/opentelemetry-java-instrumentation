/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore.trie;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class TrieTest {
  @Test
  void shouldMatchExactString() {
    Trie<Integer> trie =
        Trie.<Integer>newBuilder().put("abc", 0).put("abcd", 10).put("abcde", 20).build();

    assertNull(trie.getOrNull("ab"));
    assertEquals(0, trie.getOrNull("abc"));
    assertEquals(10, trie.getOrNull("abcd"));
    assertEquals(20, trie.getOrNull("abcde"));
  }

  @Test
  void shouldReturnLastMatchedValue() {
    Trie<Integer> trie =
        Trie.<Integer>newBuilder().put("abc", 0).put("abcde", 10).put("abcdfgh", 20).build();

    assertNull(trie.getOrNull("ababababa"));
    assertEquals(0, trie.getOrNull("abcd"));
    assertEquals(10, trie.getOrNull("abcdefgh"));
    assertEquals(20, trie.getOrNull("abcdfghjkl"));
  }

  @Test
  void shouldOverwritePreviousValue() {
    Trie<Integer> trie = Trie.<Integer>newBuilder().put("abc", 0).put("abc", 12).build();

    assertEquals(12, trie.getOrNull("abc"));
  }
}
