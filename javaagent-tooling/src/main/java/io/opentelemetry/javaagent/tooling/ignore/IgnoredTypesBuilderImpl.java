/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.tooling.ignore.trie.Trie;

public class IgnoredTypesBuilderImpl implements IgnoredTypesBuilder {
  private final Trie.Builder<IgnoreAllow> ignoredTypesTrie = Trie.newBuilder();
  private final Trie.Builder<IgnoreAllow> ignoredClassLoadersTrie = Trie.newBuilder();

  @Override
  public IgnoredTypesBuilder ignoreClass(String classNameOrPrefix) {
    ignoredTypesTrie.put(classNameOrPrefix, IgnoreAllow.IGNORE);
    return this;
  }

  @Override
  public IgnoredTypesBuilder allowClass(String classNameOrPrefix) {
    ignoredTypesTrie.put(classNameOrPrefix, IgnoreAllow.ALLOW);
    return this;
  }

  @Override
  public IgnoredTypesBuilder ignoreClassLoader(String classNameOrPrefix) {
    ignoredClassLoadersTrie.put(classNameOrPrefix, IgnoreAllow.IGNORE);
    return this;
  }

  @Override
  public IgnoredTypesBuilder allowClassLoader(String classNameOrPrefix) {
    ignoredClassLoadersTrie.put(classNameOrPrefix, IgnoreAllow.ALLOW);
    return this;
  }

  @Override
  public IgnoredTypesBuilder ignoreTaskClass(String className) {
    // TODO: collect task classes into a separate trie
    throw new UnsupportedOperationException("not implemented yet");
  }

  @Override
  public IgnoredTypesBuilder allowTaskClass(String className) {
    // TODO: collect task classes into a separate trie
    throw new UnsupportedOperationException("not implemented yet");
  }

  public Trie<IgnoreAllow> buildIgnoredTypesTrie() {
    return ignoredTypesTrie.build();
  }

  public Trie<IgnoreAllow> buildIgnoredClassLoadersTrie() {
    return ignoredClassLoadersTrie.build();
  }
}
