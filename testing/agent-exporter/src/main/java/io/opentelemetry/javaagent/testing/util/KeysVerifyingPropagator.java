/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.util;

import static java.util.Collections.emptyList;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Collection;
import javax.annotation.Nullable;

/**
 * A TextMapPropagator that verifies TextMapGetter methods can be called without errors.
 *
 * <p>This propagator performs verification during extraction but doesn't extract any context
 * itself. Its purpose is to catch compatibility issues where underlying library APIs change (e.g.,
 * NoSuchMethodError).
 *
 * <p>Note: This is a copy of the class in testing-common to avoid dependency conflicts
 */
public final class KeysVerifyingPropagator implements TextMapPropagator {
  private static final KeysVerifyingPropagator INSTANCE = new KeysVerifyingPropagator();

  public static KeysVerifyingPropagator getInstance() {
    return INSTANCE;
  }

  private KeysVerifyingPropagator() {}

  @Override
  public Collection<String> fields() {
    return emptyList();
  }

  @Override
  public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
    // This propagator doesn't inject anything
  }

  @Override
  @SuppressWarnings("OtelCanIgnoreReturnValueSuggester")
  public <C> Context extract(Context context, @Nullable C carrier, TextMapGetter<C> getter) {
    // Exercise methods to verify no errors
    Iterable<String> keys = getter.keys(carrier);
    if (keys != null) {
      keys.iterator().forEachRemaining(key -> getter.get(carrier, key));
    }

    return context;
  }
}
