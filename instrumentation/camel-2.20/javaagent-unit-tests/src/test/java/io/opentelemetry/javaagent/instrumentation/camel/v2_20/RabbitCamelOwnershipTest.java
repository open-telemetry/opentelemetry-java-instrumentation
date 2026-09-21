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

  private static final VirtualField<Consumer, Boolean> PROCESSING_SELECTION =
      VirtualField.find(Consumer.class, Boolean.class);

  @Test
  void selectsCamelProcessingBeforeConsumerRegistration() {
    Consumer consumer = mock(Consumer.class);

    RabbitConsumerInstrumentation.StartAdvice.onEnter(consumer);

    assertThat(PROCESSING_SELECTION.get(consumer))
        .isEqualTo(emitStableMessagingSemconv() ? true : null);
  }
}
