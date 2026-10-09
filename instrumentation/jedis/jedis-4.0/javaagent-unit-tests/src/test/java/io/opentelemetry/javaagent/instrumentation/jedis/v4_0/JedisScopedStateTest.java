/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v4_0.JedisSingletons.currentBatch;
import static io.opentelemetry.javaagent.instrumentation.jedis.v4_0.JedisSingletons.currentTransactionFraming;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Transaction;

class JedisScopedStateTest {

  @AfterEach
  void cleanup() {
    currentBatch().restore(null);
    currentTransactionFraming().restore(null);
  }

  @Test
  void nestedQueueRestoresOuterBatchAfterFailure() {
    Pipeline outer = mock(Pipeline.class);
    Transaction inner = mock(Transaction.class);
    Object previous = JedisPipelineInstrumentation.QueueCommandAdvice.onEnter(outer);
    assertThat(currentBatch().get()).isSameAs(outer);
    try {
      assertThatThrownBy(
              () -> {
                Object nested = JedisPipelineInstrumentation.QueueCommandAdvice.onEnter(inner);
                try {
                  assertThat(currentBatch().get()).isSameAs(inner);
                  throw new IllegalStateException("queue failed");
                } finally {
                  JedisPipelineInstrumentation.QueueCommandAdvice.stopCollecting(nested);
                }
              })
          .isInstanceOf(IllegalStateException.class);
      assertThat(currentBatch().get()).isSameAs(outer);
    } finally {
      JedisPipelineInstrumentation.QueueCommandAdvice.stopCollecting(previous);
    }
    assertThat(currentBatch().get()).isNull();
  }

  @Test
  void nestedMultiRestoresFraming() {
    Boolean previous = JedisInstrumentation.MultiAdvice.onEnter();
    Boolean nested = JedisInstrumentation.MultiAdvice.onEnter();
    JedisInstrumentation.MultiAdvice.onExit(nested);
    assertThat(JedisPipelineContext.inTransactionFraming()).isTrue();
    JedisInstrumentation.MultiAdvice.onExit(previous);
    assertThat(currentTransactionFraming().get()).isNull();
  }

  @Test
  void execAndDiscardRestoreOuterFramingOnFailure() {
    Transaction transaction = mock(Transaction.class);
    Boolean previous = JedisInstrumentation.MultiAdvice.onEnter();
    try {
      JedisTransactionInstrumentation.ExecAdvice.AdviceScope exec =
          JedisTransactionInstrumentation.ExecAdvice.onEnter(transaction);
      JedisTransactionInstrumentation.ExecAdvice.stopSpan(
          new IllegalStateException("exec failed"), exec);
      assertThat(JedisPipelineContext.inTransactionFraming()).isTrue();

      Boolean discard = JedisTransactionInstrumentation.DiscardAdvice.onEnter(transaction);
      JedisTransactionInstrumentation.DiscardAdvice.onExit(discard);
      assertThat(JedisPipelineContext.inTransactionFraming()).isTrue();
    } finally {
      JedisInstrumentation.MultiAdvice.onExit(previous);
    }
    assertThat(currentTransactionFraming().get()).isNull();
  }
}
