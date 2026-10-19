/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.common.v1_4;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

class JedisBatchResponseTest {
  @Test
  void errorCodeLengthBoundary() {
    String code = new String(new char[64]).replace('\0', 'A');
    assertThat(JedisBatchResponse.getErrorType(code)).isEqualTo(code);
    assertThat(JedisBatchResponse.getErrorType(code + " server message")).isEqualTo(code);
    assertThat(JedisBatchResponse.getErrorType(code + "A")).isNull();
    assertThat(JedisBatchResponse.getErrorType(code + "A server message")).isNull();
    assertThat(JedisBatchResponse.getErrorType(code + "a server message")).isNull();
  }

  @Test
  void extractsOnlyProtocolCodes() {
    assertThat(JedisBatchResponse.getErrorType("WRONGTYPE wrong kind of value"))
        .isEqualTo("WRONGTYPE");
    assertThat(JedisBatchResponse.getErrorType("CLUSTER_DOWN unavailable"))
        .isEqualTo("CLUSTER_DOWN");
    assertThat(JedisBatchResponse.getErrorType("ERR")).isEqualTo("ERR");
    assertThat(JedisBatchResponse.getErrorType("not a protocol error")).isNull();
    assertThat(JedisBatchResponse.getErrorType(" ERR")).isNull();
    assertThat(JedisBatchResponse.getErrorType("ERR\nmessage")).isNull();
    assertThat(JedisBatchResponse.getErrorType("")).isNull();
    assertThat(JedisBatchResponse.getErrorType(null)).isNull();
  }

  @Test
  void ignoresSuccessfulMixedIncompleteAndUnrelatedReplies() {
    Object connection = new Object();
    Context context = JedisBatchResponse.store(Context.current(), connection, 2);
    Throwable failure = new IllegalStateException("WRONGTYPE wrong kind of value");
    try (Scope ignored = context.makeCurrent()) {
      JedisBatchResponse.capture(new Object(), asList(failure, failure));
      JedisBatchResponse.capture(connection, singletonList(failure));
      JedisBatchResponse.capture(connection, emptyList());
      JedisBatchResponse.capture(connection, null);
      JedisBatchResponse.capture(connection, asList(0L, 0L));
      JedisBatchResponse.capture(connection, asList(failure, 0L));
      JedisBatchResponse.capture(connection, asList(0L, failure));
    }
    assertThat(JedisBatchResponse.getError(context, null)).isNull();
  }

  @Test
  void terminalFailureTakesPrecedenceAndCaptureIsScoped() {
    Object connection = new Object();
    Context context = JedisBatchResponse.store(Context.current(), connection, 2);
    Throwable failure = new IllegalStateException("WRONGTYPE wrong kind of value");
    Throwable terminal = new IllegalStateException("connection closed");
    JedisBatchResponse.capture(connection, asList(failure, failure));
    assertThat(JedisBatchResponse.getError(context, null)).isNull();
    try (Scope ignored = context.makeCurrent()) {
      JedisBatchResponse.capture(connection, asList(failure, failure));
    }
    assertThat(JedisBatchResponse.getError(context, null)).isSameAs(failure);
    assertThat(JedisBatchResponse.getError(context, terminal)).isSameAs(terminal);
    assertThat(JedisBatchResponse.getError(Context.current(), null)).isNull();
    assertThat(JedisBatchResponse.getError(null, terminal)).isSameAs(terminal);
  }
}
