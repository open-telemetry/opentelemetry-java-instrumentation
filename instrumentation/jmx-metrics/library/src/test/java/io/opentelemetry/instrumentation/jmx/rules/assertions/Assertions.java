/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.rules.assertions;

import io.opentelemetry.proto.metrics.v1.Metric;

/** Dedicated Assertj extension to provide convenient fluent API for metrics testing */
public class Assertions extends org.assertj.core.api.Assertions {

  public static MetricAssert assertThat(Metric metric) {
    return new MetricAssert(metric);
  }
}
