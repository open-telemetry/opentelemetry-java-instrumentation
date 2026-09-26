/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableMessagingSemconv;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.rabbitmq.client.Consumer;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.junit.jupiter.api.Test;

class RabbitCamelOwnershipTest {

  private static final VirtualField<Consumer, Boolean> PROCESSING_OWNED_OUTSIDE_RABBIT_CLIENT =
      VirtualField.find(Consumer.class, Boolean.class);

  @Test
  void marksCamelProcessingBeforeConsumerRegistration() {
    Consumer consumer = mock(Consumer.class);

    RabbitConsumerInstrumentation.StartAdvice.onEnter(consumer);

    assertThat(PROCESSING_OWNED_OUTSIDE_RABBIT_CLIENT.get(consumer))
        .isEqualTo(emitStableMessagingSemconv() ? true : null);
  }
}
