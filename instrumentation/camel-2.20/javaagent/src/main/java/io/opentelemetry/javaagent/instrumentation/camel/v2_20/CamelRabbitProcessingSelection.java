/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import com.rabbitmq.client.Consumer;
import io.opentelemetry.instrumentation.api.util.VirtualField;

public final class CamelRabbitProcessingSelection {

  private static final VirtualField<Consumer, Boolean> PROCESSING_SELECTION =
      VirtualField.find(Consumer.class, Boolean.class);

  public static void selectFrameworkProcessing(Consumer consumer) {
    PROCESSING_SELECTION.set(consumer, true);
  }

  private CamelRabbitProcessingSelection() {}
}
