/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.testing.propagator;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.propagation.TextMapPropagator;
import io.opentelemetry.javaagent.testing.util.KeysVerifyingPropagator;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.ConfigurablePropagatorProvider;

/** Provides a composite propagator that includes the default W3C propagators plus verification. */
@AutoService(ConfigurablePropagatorProvider.class)
public final class KeysVerifyingPropagatorProvider implements ConfigurablePropagatorProvider {
  @Override
  public TextMapPropagator getPropagator(ConfigProperties configProperties) {
    return KeysVerifyingPropagator.getInstance();
  }

  @Override
  public String getName() {
    return "testing-verifying-propagator";
  }
}
