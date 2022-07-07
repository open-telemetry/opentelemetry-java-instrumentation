/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.kafka;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.spring.kafka.v2_7.SpringKafkaTelemetry;

public final class SpringKafkaSingletons {

  private static final SpringKafkaTelemetry TELEMETRY =
      SpringKafkaTelemetry.create(GlobalOpenTelemetry.get());

  public static SpringKafkaTelemetry telemetry() {
    return TELEMETRY;
  }

  private SpringKafkaSingletons() {}
}
