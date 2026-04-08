/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.util;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TrieTest {
  @Test
  void shouldMatchExactString() {
    Trie<Integer> trie =
        Trie.<Integer>builder().put("abc", 0).put("abcd", 10).put("abcde", 20).build();

    assertThat(trie.getOrNull("ab")).isNull();
    assertThat(trie.contains("ab")).isFalse();
    assertThat(trie.getOrNull("abc")).isEqualTo(0);
    assertThat(trie.getOrNull("abcd")).isEqualTo(10);
    assertThat(trie.getOrNull("abcde")).isEqualTo(20);
    assertThat(trie.contains("abcde")).isTrue();
  }

  @Test
  void shouldReturnLastMatchedValue() {
    Trie<Integer> trie =
        Trie.<Integer>builder().put("abc", 0).put("abcde", 10).put("abcdfgh", 20).build();

    assertThat(trie.getOrNull("ababababa")).isNull();
    assertThat(trie.getOrNull("abcd")).isEqualTo(0);
    assertThat(trie.getOrNull("abcdefgh")).isEqualTo(10);
    assertThat(trie.getOrNull("abcdfghjkl")).isEqualTo(20);
  }

  @Test
  void shouldOverwritePreviousValue() {
    Trie<Integer> trie = Trie.<Integer>builder().put("abc", 0).put("abc", 12).build();

    assertThat(trie.getOrNull("abc")).isEqualTo(12);
  }

  @Test
  void shouldReturnDefaultValueWhenNotMatched() {
    Trie<Integer> trie = Trie.<Integer>builder().put("abc", 42).build();

    assertThat(trie.getOrDefault("acdc", -1)).isEqualTo(-1);
  }
}
