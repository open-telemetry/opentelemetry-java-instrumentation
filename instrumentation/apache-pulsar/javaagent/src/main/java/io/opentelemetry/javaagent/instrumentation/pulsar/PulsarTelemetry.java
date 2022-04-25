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

  public static final Tracer TRACER = GlobalOpenTelemetry.getTracer(INSTRUMENTATION);
  public static final TextMapPropagator PROPAGATOR =
      GlobalOpenTelemetry.getPropagators().getTextMapPropagator();

  private PulsarTelemetry() {}
}
