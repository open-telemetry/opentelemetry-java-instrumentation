/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryClaims;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetryCarrier;
import org.apache.camel.Message;

public class CamelMessageTelemetry {

  private static final MessagingTelemetryCarrier<Message> messageTelemetry =
      MessagingTelemetryCarrier.create(
          VirtualField.find(Message.class, MessagingTelemetryClaims.class));

  public static MessagingTelemetryCarrier<Message> messageTelemetry() {
    return messageTelemetry;
  }

  private CamelMessageTelemetry() {}
}
