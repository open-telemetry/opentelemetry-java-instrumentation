/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pulsar;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.propagation.TextMapPropagator;

public final class PulsarTelemetry {

  private static final String INSTRUMENTATION = "io.opentelemetry:pulsar-client";
  public static final AttributeKey<String> TOPIC = AttributeKey.stringKey("messaging_pulsar_topic");
  public static final AttributeKey<String> SERVICE_URL =
      AttributeKey.stringKey("messaging_pulsar_service_url");
  public static final AttributeKey<String> SUBSCRIPTION =
      AttributeKey.stringKey("messaging_pulsar_subscription");
  public static final AttributeKey<String> PRODUCER_NAME =
      AttributeKey.stringKey("messaging_pulsar_producer_name");
  public static final AttributeKey<String> CONSUMER_NAME =
      AttributeKey.stringKey("messaging_pulsar_consumer_name");
  public static final AttributeKey<String> MESSAGE_ID =
      AttributeKey.stringKey("messaging_pulsar_message_id");

  private PulsarTelemetry() {}

  public static Tracer tracer() {
    return GlobalOpenTelemetry.get().getTracer(INSTRUMENTATION);
  }

  public static TextMapPropagator propagator() {
    return GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator();
  }
}
