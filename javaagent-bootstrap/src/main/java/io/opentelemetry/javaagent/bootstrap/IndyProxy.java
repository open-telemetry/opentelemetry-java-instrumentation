/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.lang.reflect.Field;

/** Interface added to proxy classes injected for indy instrumentation */
@SuppressWarnings("InterfaceWithOnlyStatics") // TODO: remove this once we have a method
public interface IndyProxy {

  /**
   * Unwraps the proxied object
   *
   * @return unwrapped object
   */
  default Object unwrap() {
    try {
      // current implementation based on introspection + public delegate field
      Field delegate = this.getClass().getField("delegate");
      return delegate.get(this);
    } catch (NoSuchFieldException | IllegalAccessException e) {
      throw new IllegalStateException(e);
    }
  }

  /**
   * Transparently unwraps an object that might have been proxied for indy instrumentation. When
   * unwrapping is not needed, for example for inlined advices, the original object is returned
   * effectively making this equivalent to a type cast.
   *
   * @param o object that might need unwrapping
   * @param type expected unwrapped object type
   * @param <T> type of the proxied object
   * @return unwrapped object
   * @throws IllegalArgumentException when object can't be cast or unwrapped to the desired type
   */
  static <T> T unwrapIfNeeded(Object o, Class<T> type) {
    if (type.isAssignableFrom(o.getClass())) {
      return type.cast(o);
    }
    if (o instanceof IndyProxy) {
      Object unwrapped = ((IndyProxy) o).unwrap();
      if (type.isAssignableFrom(unwrapped.getClass())) {
        return type.cast(unwrapped);
      }
    }
    throw new IllegalArgumentException("unexpected object unwrap");
  }
}
