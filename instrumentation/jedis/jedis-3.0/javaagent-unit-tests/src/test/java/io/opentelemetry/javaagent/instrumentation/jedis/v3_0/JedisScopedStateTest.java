/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static io.opentelemetry.javaagent.instrumentation.jedis.v3_0.JedisSingletons.currentPipeline;
import static io.opentelemetry.javaagent.instrumentation.jedis.v3_0.JedisSingletons.currentTransactionFraming;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.PipelineBase;
import redis.clients.jedis.Transaction;

class JedisScopedStateTest {

  @AfterEach
  void cleanup() {
    currentPipeline().restore(null);
    currentTransactionFraming().restore(null);
  }

  @Test
  void nestedQueueRestoresOuterPipelineAfterFailure() {
    Pipeline outer = mock(Pipeline.class);
    Pipeline inner = mock(Pipeline.class);
    PipelineBase previous = JedisPipelineInstrumentation.QueueCommandAdvice.onEnter(outer);
    assertThat(currentPipeline().get()).isSameAs(outer);
    try {
      assertThatThrownBy(
              () -> {
                PipelineBase nested =
                    JedisPipelineInstrumentation.QueueCommandAdvice.onEnter(inner);
                try {
                  assertThat(currentPipeline().get()).isSameAs(inner);
                  throw new IllegalStateException("queue failed");
                } finally {
                  JedisPipelineInstrumentation.QueueCommandAdvice.stopCollecting(nested);
                }
              })
          .isInstanceOf(IllegalStateException.class);
      assertThat(currentPipeline().get()).isSameAs(outer);
    } finally {
      JedisPipelineInstrumentation.QueueCommandAdvice.stopCollecting(previous);
    }
    assertThat(currentPipeline().get()).isNull();
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
