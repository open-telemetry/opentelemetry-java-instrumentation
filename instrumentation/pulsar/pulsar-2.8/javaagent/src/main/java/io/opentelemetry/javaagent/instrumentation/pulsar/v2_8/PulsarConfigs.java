/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar.v2_8;

public class PulsarConfigs {

  public static final String METRICS_CONFIG_NAME =
      "otel.instrumentation.pulsar-clients-metrics.enabled";
  public static final String EXPERIMENTAL_SPAN_ATTRIBUTES_NAME =
      "otel.instrumentation.pulsar.experimental-span-attributes";

  private PulsarConfigs() {}
}
