/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.util;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Collection;
import javax.annotation.Nullable;

/**
 * A TextMapPropagator wrapper that verifies TextMapGetter methods can be called without errors.
 *
 * <p>This propagator wraps another propagator and calls methods during extraction. This catches
 * compatibility issues where underlying library APIs change (e.g., NoSuchMethodError).
 *
 * <p>Note: This is a copy of the class in testing-common to avoid dependency conflicts
 */
public final class KeysVerifyingPropagator implements TextMapPropagator {
  private final TextMapPropagator delegate;

  public KeysVerifyingPropagator(TextMapPropagator delegate) {
    this.delegate = delegate;
  }

  @Override
  public Collection<String> fields() {
    return delegate.fields();
  }

  @Override
  public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
    delegate.inject(context, carrier, setter);
  }

  @Override
  public <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter) {
    // exercise methods to verify no errors
    Iterable<String> keys = getter.keys(carrier);
    if (keys != null) {
      keys.iterator().forEachRemaining(key -> getter.get(carrier, key));
    }

    return delegate.extract(context, carrier, getter);
  }
}
