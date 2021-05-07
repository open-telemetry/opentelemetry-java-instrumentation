/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for OpenTelemetry Sampler.
 *
 * <p>Get Sampling Probability
 */
@ConfigurationProperties(prefix = "otel.traces.sampler")
public final class SamplerProperties {

  // if Sample probability == 1: always sample
  // if Sample probability == 0: never sample
  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double probability = 1.0;

  public double getProbability() {
    return probability;
  }

  public void setProbability(double probability) {
    this.probability = probability;
  }
}
