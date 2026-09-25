/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.sampler.internal;

import io.opentelemetry.sdk.autoconfigure.spi.ConfigProperties;
import io.opentelemetry.sdk.autoconfigure.spi.traces.ConfigurableSamplerProvider;
import io.opentelemetry.sdk.trace.samplers.Sampler;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class LinksParentAlwaysOnSamplerProvider implements ConfigurableSamplerProvider {
  @Override
  public Sampler createSampler(ConfigProperties config) {
    return new LinksBasedSampler(Sampler.parentBased(Sampler.alwaysOn()));
  }

  @Override
  public String getName() {
    return "linksbased_parentbased_always_on";
  }
}
