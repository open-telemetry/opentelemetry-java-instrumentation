/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.auto.api;

public final class Pair<T, U> {

  public static <T, U> Pair<T, U> of(T left, U right) {
    return new Pair<>(left, right);
  }

  private final T left;
  private final U right;

  Pair(T left, U right) {
    this.left = left;
    this.right = right;
  }

  public T getLeft() {
    return left;
  }

  public U getRight() {
    return right;
  }

  public boolean hasLeft() {
    return null != left;
  }

  public boolean hasRight() {
    return null != right;
  }
}
