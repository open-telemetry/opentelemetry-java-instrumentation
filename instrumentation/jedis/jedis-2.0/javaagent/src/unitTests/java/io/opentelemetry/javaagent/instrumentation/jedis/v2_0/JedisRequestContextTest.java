/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.javaagent.instrumentation.jedis.common.JedisRequestContext;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JedisRequestContextTest {
  @ParameterizedTest
  @MethodSource("batchCompletions")
  void selectsBatchFailure(int terminalFailure, int expectedFailure) {
    JedisRequestContext<Object> context = JedisRequestContext.attachForBatch(new Object());
    Throwable first = new IllegalStateException("first server failure");
    Throwable last = new IllegalStateException("last server failure");
    Throwable client = new IllegalStateException("WRONGTYPE client failure");
    try {
      capture(first);
      capture(last);
      Throwable terminal =
          terminalFailure == 0
              ? null
              : terminalFailure == 1 ? first : terminalFailure == 2 ? last : client;
      Throwable expected = expectedFailure == 0 ? first : expectedFailure == 1 ? last : client;
      assertThat(context.detachAndEnd(terminal)).isSameAs(expected);
    } finally {
      context.detachAndEnd(null);
    }
    assertDetached();
  }

  private static Stream<Arguments> batchCompletions() {
    return Stream.of(
        argumentSet("returned failures use first result", 0, 0),
        argumentSet("terminal first server failure", 1, 0),
        argumentSet("terminal later server failure", 2, 1),
        argumentSet("terminal client failure", 3, 2));
  }

  @Test
  void propagatesCommandFailure() {
    JedisRequestContext<Object> context = JedisRequestContext.attach();
    Throwable failure = new IllegalStateException("WRONGTYPE client failure");
    try {
      assertThat(context.detachAndEnd(failure)).isSameAs(failure);
    } finally {
      context.detachAndEnd(null);
    }
    assertDetached();
  }

  private static void capture(Throwable throwable) {
    JedisRequestContext.captureServerError(throwable);
  }

  private static void assertDetached() {
    JedisRequestContext<Object> next = JedisRequestContext.attach();
    try {
      assertThat(next).isNotNull();
      assertThat(next.detachAndEnd(null)).isNull();
    } finally {
      if (next != null) {
        next.detachAndEnd(null);
      }
    }
  }
}
