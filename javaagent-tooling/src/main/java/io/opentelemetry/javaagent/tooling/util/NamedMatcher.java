/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.util;

import net.bytebuddy.matcher.ElementMatcher;

/**
 * A matcher wrapper that adds specified name to the output of {@code toString} to allow easy
 * identification of where the given matcher originates from.
 */
public class NamedMatcher<T> implements ElementMatcher<T> {
  private final String name;
  private final ElementMatcher<T> delegate;

  public NamedMatcher(String name, ElementMatcher<T> delegate) {
    this.name = name;
    this.delegate = delegate;
  }

  @Override
  public boolean matches(T target) {
    return delegate.matches(target);
  }

  @Override
  public String toString() {
    return name + "(" + delegate.toString() + ")";
  }
}
