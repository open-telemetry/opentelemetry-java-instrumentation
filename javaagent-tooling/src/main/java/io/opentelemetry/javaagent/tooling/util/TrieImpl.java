/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.util;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

final class TrieImpl<V> implements Trie<V> {

  private final Node<V> root;

  private TrieImpl(Node<V> root) {
    this.root = root;
  }

  @Override
  public V getOrDefault(CharSequence str, V defaultValue) {
    Node<V> node = root;
    V lastMatchedValue = defaultValue;

    for (int i = 0; i < str.length(); ++i) {
      char c = str.charAt(i);
      Node<V> next = node.getNext(c);
      if (next == null) {
        return lastMatchedValue;
      }
      node = next;
      // next node matched, use its value if it's defined
      lastMatchedValue = next.value != null ? next.value : lastMatchedValue;
    }

    return lastMatchedValue;
  }

  static final class Node<V> {
    final char[] chars;
    final Node<V>[] children;
    final V value;

    Node(char[] chars, Node<V>[] children, V value) {
      this.chars = chars;
      this.children = children;
      this.value = value;
    }

    @Nullable
    Node<V> getNext(char c) {
      int index = Arrays.binarySearch(chars, c);
      if (index < 0) {
        return null;
      }
      return children[index];
    }
  }

  static final class BuilderImpl<V> implements Builder<V> {

    private final NodeBuilder<V> root = new NodeBuilder<>();

    @Override
    public Builder<V> put(CharSequence str, V value) {
      put(root, str, 0, value);
      return this;
    }

    private void put(NodeBuilder<V> node, CharSequence str, int i, V value) {
      if (str.length() == i) {
        node.value = value;
        return;
      }
      char c = str.charAt(i);
      NodeBuilder<V> next = node.children.computeIfAbsent(c, k -> new NodeBuilder<>());
      put(next, str, i + 1, value);
    }

    @Override
    public Trie<V> build() {
      return new TrieImpl<>(root.build());
    }
  }

  static final class NodeBuilder<V> {
    final Map<Character, NodeBuilder<V>> children = new HashMap<>();
    V value;

    Node<V> build() {
      int size = children.size();
      char[] chars = new char[size];
      Node<V>[] nodes = new Node[size];

      int i = 0;
      Iterator<Map.Entry<Character, NodeBuilder<V>>> it =
          this.children.entrySet().stream().sorted(Map.Entry.comparingByKey()).iterator();
      while (it.hasNext()) {
        Map.Entry<Character, NodeBuilder<V>> e = it.next();
        chars[i] = e.getKey();
        nodes[i++] = e.getValue().build();
      }

      return new Node<>(chars, nodes, value);
    }
  }
}
