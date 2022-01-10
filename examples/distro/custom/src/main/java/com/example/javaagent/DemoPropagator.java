/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package com.example.javaagent;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.ContextKey;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.context.propagation.TextMapSetter;
import java.util.Collections;
import java.util.List;

/**
 * See <a
 * href="https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/context/api-propagators.md">
 * OpenTelemetry Specification</a> for more information about Propagators.
 *
 * @see DemoPropagatorProvider
 */
public class DemoPropagator implements TextMapPropagator {
  private static final String FIELD = "X-demo-field";
  private static final ContextKey<Long> PROPAGATION_START_KEY =
      ContextKey.named("propagation.start");

  @Override
  public List<String> fields() {
    return Collections.singletonList(FIELD);
  }

  @Override
  public <C> void inject(Context context, C carrier, TextMapSetter<C> setter) {
    Long propagationStart = context.get(PROPAGATION_START_KEY);
    if (propagationStart == null) {
      propagationStart = System.currentTimeMillis();
    }
    setter.set(carrier, FIELD, String.valueOf(propagationStart));
  }

  @Override
  public <C> Context extract(Context context, C carrier, TextMapGetter<C> getter) {
    String propagationStart = getter.get(carrier, FIELD);
    if (propagationStart != null) {
      return context.with(PROPAGATION_START_KEY, Long.valueOf(propagationStart));
    } else {
      return context;
    }
  }
}
