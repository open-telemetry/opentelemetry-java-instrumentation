/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure;

import javax.validation.constraints.DecimalMax;
import javax.validation.constraints.DecimalMin;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration for OpenTelemetry Tracer.
 *
 * <p>Get Tracer Name
 *
 * <p>Get Sampling Probability
 */
@ConfigurationProperties(prefix = "opentelemetry.trace.tracer")
public final class TracerProperties {

  private String name = "io.opentelemetry.instrumentation.spring-boot-autoconfigure";

  // if Sample probability == 1: always sample
  // if Sample probability == 0: never sample
  @DecimalMin("0.0")
  @DecimalMax("1.0")
  private double samplerProbability = 1.0;

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public double getSamplerProbability() {
    return samplerProbability;
  }

  public void setSamplerProbability(double samplerProbability) {
    this.samplerProbability = samplerProbability;
  }
}
