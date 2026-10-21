/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.integration.v4_1;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.spring.integration.v4_1.internal.SpringIntegrationHandoff;
import javax.annotation.Nullable;
import org.springframework.amqp.core.Message;

public final class SpringRabbitProcessingContext {

  // Spring Rabbit writes the same typed field while it owns processing for this raw delivery.
  private static final VirtualField<Message, Context> SPRING_RABBIT_PROCESSING_CONTEXT =
      VirtualField.find(Message.class, Context.class);

  @Nullable
  public static Object enter(Message message) {
    return SpringIntegrationHandoff.enterLowerProcessing(
        SPRING_RABBIT_PROCESSING_CONTEXT.get(message));
  }

  public static void exit(@Nullable Object state) {
    SpringIntegrationHandoff.exitLowerProcessing(state);
  }

  private SpringRabbitProcessingContext() {}
}
