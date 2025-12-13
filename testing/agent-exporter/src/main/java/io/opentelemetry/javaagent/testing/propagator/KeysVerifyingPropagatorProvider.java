/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.propagator;

import com.google.auto.service.AutoService;
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.javaagent.testing.util.KeysVerifyingPropagator;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurablePropagatorProvider;

/** Provides a propagator that wraps the default W3C propagators with additional verification. */
@AutoService(ConfigurablePropagatorProvider.class)
public final class KeysVerifyingPropagatorProvider implements ConfigurablePropagatorProvider {
  @Override
  public TextMapPropagator getPropagator(ConfigProperties configProperties) {
    TextMapPropagator composite =
        TextMapPropagator.composite(
            W3CTraceContextPropagator.getInstance(), W3CBaggagePropagator.getInstance());

    return new KeysVerifyingPropagator(composite);
  }

  @Override
  public String getName() {
    return "testing-verifying-propagator";
  }
}
