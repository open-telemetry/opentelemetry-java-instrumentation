/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import net.bytebuddy.matcher.ElementMatcher;

final class CallWhenTrueDecorator<T> extends ElementMatcher.Junction.AbstractBase<T> {
  private final ElementMatcher<? super T> delegate;
  private final Runnable callback;

  CallWhenTrueDecorator(ElementMatcher<? super T> delegate, Runnable callback) {
    this.delegate = delegate;
    this.callback = callback;
  }

  @Override
  public boolean matches(T target) {
    if (delegate.matches(target)) {
      callback.run();
      return true;
    }
    return false;
  }
}
