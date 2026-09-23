/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import com.rabbitmq.client.Consumer;
import io.opentelemetry.instrumentation.api.util.VirtualField;

public final class CamelRabbitProcessingOwnership {

  private static final VirtualField<Consumer, Boolean> PROCESSING_OWNED_OUTSIDE_RABBIT_CLIENT =
      VirtualField.find(Consumer.class, Boolean.class);

  public static void markCamelAsProcessingOwner(Consumer consumer) {
    PROCESSING_OWNED_OUTSIDE_RABBIT_CLIENT.set(consumer, true);
  }

  private CamelRabbitProcessingOwnership() {}
}
