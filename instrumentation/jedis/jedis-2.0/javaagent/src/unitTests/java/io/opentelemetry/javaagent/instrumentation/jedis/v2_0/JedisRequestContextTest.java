/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.javaagent.instrumentation.jedis.common.JedisRequestContext;
import io.opentelemetry.javaagent.instrumentation.jedis.common.JedisServerErrorRequest;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class JedisRequestContextTest {
  @Test
  void recoversUtf8FromConfirmedDecoderReply() {
    TestRequest request = new TestRequest();
    JedisRequestContext<TestRequest> context = JedisRequestContext.attachForBatch(request);
    try {
      capture("ￃﾩchec details", new IllegalStateException("server failure"));
      context.detachAndEnd(null);
      assertThat(request.reply).isEqualTo("échec details");
    } finally {
      context.detachAndEnd(null);
    }
    assertDetached();
  }

  @ParameterizedTest
  @MethodSource("batchCompletions")
  void selectsBatchFailure(int terminalFailure, String expectedReply) {
    TestRequest request = new TestRequest();
    JedisRequestContext<TestRequest> context = JedisRequestContext.attachForBatch(request);
    Throwable first = new IllegalStateException("first server failure");
    Throwable last = new IllegalStateException("last server failure");
    Throwable client = new IllegalStateException("WRONGTYPE client failure");
    try {
      capture("WRONGTYPE first", first);
      capture("Custom.Code! last", last);
      Throwable terminal =
          terminalFailure == 0
              ? null
              : terminalFailure == 1 ? first : terminalFailure == 2 ? last : client;
      assertThat(context.detachAndEnd(terminal)).isSameAs(terminal == null ? first : terminal);
      assertThat(request.reply).isEqualTo(expectedReply);
    } finally {
      context.detachAndEnd(null);
    }
    assertDetached();
  }

  private static Stream<Arguments> batchCompletions() {
    return Stream.of(
        argumentSet("returned failures use first result", 0, "WRONGTYPE first"),
        argumentSet("terminal first server failure", 1, "WRONGTYPE first"),
        argumentSet("terminal later server failure", 2, "Custom.Code! last"),
        argumentSet("terminal client failure", 3, null));
  }

  @ParameterizedTest
  @MethodSource("commandCompletions")
  void refreshesCommandAttempt(boolean fail, String expectedReply) {
    TestRequest request = new TestRequest();
    JedisRequestContext<TestRequest> context = JedisRequestContext.attach();
    Throwable first = new IllegalStateException("redirect");
    Throwable last = new IllegalStateException("terminal server failure");
    try {
      capture("MOVED 42 127.0.0.1:6379", first);
      capture("WRONGTYPE terminal", last);
      Throwable terminal = fail ? last : null;
      assertThat(context.detachAndEnd(terminal)).isSameAs(terminal);
      context.applyServerErrorReply(request, terminal);
      assertThat(request.reply).isEqualTo(expectedReply);
    } finally {
      context.detachAndEnd(null);
    }
    assertDetached();
  }

  private static Stream<Arguments> commandCompletions() {
    return Stream.of(
        argumentSet("terminal server failure", true, "WRONGTYPE terminal"),
        argumentSet("recovered command", false, null));
  }

  private static void capture(String reply, Throwable throwable) {
    boolean previous = JedisRequestContext.beginServerErrorReply();
    JedisRequestContext.captureServerErrorReply(reply);
    JedisRequestContext.endServerErrorReply(previous, throwable);
  }

  private static void assertDetached() {
    JedisRequestContext<TestRequest> next = JedisRequestContext.attach();
    try {
      assertThat(next).isNotNull();
      assertThat(next.detachAndEnd(null)).isNull();
    } finally {
      if (next != null) {
        next.detachAndEnd(null);
      }
    }
  }

  private static class TestRequest implements JedisServerErrorRequest {
    private String reply;

    @Override
    public void setServerErrorReply(String reply) {
      this.reply = reply;
    }
  }
}
