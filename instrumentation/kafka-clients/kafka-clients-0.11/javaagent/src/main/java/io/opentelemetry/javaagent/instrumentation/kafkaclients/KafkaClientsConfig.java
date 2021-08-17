/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import io.opentelemetry.instrumentation.api.config.Config;

public final class KafkaClientsConfig {

  private static final boolean CLIENT_PROPAGATION_ENABLED =
      Config.get()
          .getBooleanProperty("otel.instrumentation.kafka.client-propagation.enabled", true);

  private static final boolean CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES =
      Config.get()
          .getBooleanProperty("otel.instrumentation.kafka.experimental-span-attributes", false);

  public static boolean isPropagationEnabled() {
    return CLIENT_PROPAGATION_ENABLED;
  }

  public static boolean captureExperimentalSpanAttributes() {
    return CAPTURE_EXPERIMENTAL_SPAN_ATTRIBUTES;
  }

  private KafkaClientsConfig() {}
}
