/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rediscala.v1_8;

import static io.opentelemetry.javaagent.instrumentation.rediscala.v1_8.RediscalaSingletons.TRANSACTION_CLIENT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.opentelemetry.instrumentation.api.util.VirtualField;
import org.junit.jupiter.api.Test;
import redis.commands.TransactionBuilder;

class TransactionBuilderInstrumentationTest {

  @Test
  void capturesClientDirectlyWithoutOverwritingUnnamedField() {
    TransactionBuilder builder = mock(TransactionBuilder.class);
    Object unrelated = new Object();
    Object client = new Object();
    VirtualField<TransactionBuilder, Object> unnamed =
        VirtualField.find(TransactionBuilder.class, Object.class);
    unnamed.set(builder, unrelated);

    TransactionBuilderInstrumentation.CreateAdvice.onExit(client, builder);

    assertThat(TRANSACTION_CLIENT.get(builder)).isSameAs(client);
    assertThat(unnamed.get(builder)).isSameAs(unrelated);

    Object replacement = new Object();
    TransactionBuilderInstrumentation.CreateAdvice.onExit(replacement, builder);

    assertThat(TRANSACTION_CLIENT.get(builder)).isSameAs(replacement);
    assertThat(unnamed.get(builder)).isSameAs(unrelated);
  }
}
