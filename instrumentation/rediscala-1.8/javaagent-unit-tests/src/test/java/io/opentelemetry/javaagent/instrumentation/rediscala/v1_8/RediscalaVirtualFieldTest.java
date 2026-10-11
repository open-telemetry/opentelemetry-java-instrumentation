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

class RediscalaVirtualFieldTest {

  @Test
  void storesClientDirectlyWithoutOverwritingUnnamedField() {
    TransactionBuilder builder = mock(TransactionBuilder.class);
    Object unrelated = new Object();
    Object client = new Object();
    VirtualField<TransactionBuilder, Object> unnamed =
        VirtualField.find(TransactionBuilder.class, Object.class);
    unnamed.set(builder, unrelated);

    TRANSACTION_CLIENT.set(builder, client);

    assertThat(TRANSACTION_CLIENT.get(builder)).isSameAs(client);
    assertThat(unnamed.get(builder)).isSameAs(unrelated);

    Object replacement = new Object();
    TRANSACTION_CLIENT.set(builder, replacement);

    assertThat(TRANSACTION_CLIENT.get(builder)).isSameAs(replacement);
    assertThat(unnamed.get(builder)).isSameAs(unrelated);
  }
}
