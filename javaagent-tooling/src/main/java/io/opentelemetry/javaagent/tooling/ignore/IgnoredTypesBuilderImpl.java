/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.tooling.util.Trie;

public class IgnoredTypesBuilderImpl implements IgnoredTypesBuilder {
  private final Trie.Builder<IgnoreAllow> ignoredTypesTrie = Trie.newBuilder();
  private final Trie.Builder<IgnoreAllow> ignoredClassLoadersTrie = Trie.newBuilder();
  private final Trie.Builder<Boolean> ignoredTasksTrie = Trie.newBuilder();

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
  public IgnoredTypesBuilder ignoreTaskClass(String classNameOrPrefix) {
    ignoredTasksTrie.put(classNameOrPrefix, true);
    return this;
  }

  public Trie<IgnoreAllow> buildIgnoredTypesTrie() {
    return ignoredTypesTrie.build();
  }

  public Trie<IgnoreAllow> buildIgnoredClassLoadersTrie() {
    return ignoredClassLoadersTrie.build();
  }

  public Trie<Boolean> buildIgnoredTasksTrie() {
    return ignoredTasksTrie.build();
  }
}
