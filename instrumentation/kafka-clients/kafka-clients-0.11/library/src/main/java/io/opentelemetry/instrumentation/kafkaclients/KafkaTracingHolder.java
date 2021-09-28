/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.kafkaclients;

import io.opentelemetry.api.GlobalOpenTelemetry;

abstract class KafkaTracingHolder {

  private KafkaTracing tracing;

  public synchronized KafkaTracing getTracing() {
    if (tracing == null) {
      tracing = KafkaTracing.create(GlobalOpenTelemetry.get());
    }
    return tracing;
  }
}
