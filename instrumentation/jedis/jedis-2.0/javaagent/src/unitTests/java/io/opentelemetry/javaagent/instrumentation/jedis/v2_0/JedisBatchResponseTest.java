/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.jedis.common.v1_4.JedisBatchResponse;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.exceptions.JedisConnectionException;
import redis.clients.jedis.exceptions.JedisDataException;

class JedisBatchResponseTest {
  @Test
  void terminalFailureTakesPrecedenceOverReturnedErrors() {
    Object connection = new Object();
    Context context = JedisBatchResponse.store(Context.current(), connection, 2);
    JedisDataException reply = new JedisDataException("WRONGTYPE wrong kind of value");
    JedisConnectionException terminal = new JedisConnectionException("connection closed");
    try (Scope ignored = context.makeCurrent()) {
      JedisBatchResponse.capture(connection, asList(reply, reply));
    }
    assertThat(JedisBatchResponse.getError(context, null)).isSameAs(reply);
    assertThat(JedisBatchResponse.getError(context, terminal)).isSameAs(terminal);
    assertThat(JedisBatchResponse.getError(Context.current(), null)).isNull();
  }

  @Test
  void ignoresUnrelatedConnectionAndIncompleteReplies() {
    Object connection = new Object();
    Context context = JedisBatchResponse.store(Context.current(), connection, 2);
    JedisDataException reply = new JedisDataException("WRONGTYPE wrong kind of value");
    try (Scope ignored = context.makeCurrent()) {
      JedisBatchResponse.capture(new Object(), asList(reply, reply));
      JedisBatchResponse.capture(connection, singletonList(reply));
      JedisBatchResponse.capture(connection, emptyList());
      JedisBatchResponse.capture(connection, null);
    }
    assertThat(JedisBatchResponse.getError(context, null)).isNull();
  }

  @Test
  void preservesProtocolCodeWithoutServerMessage() {
    assertThat(JedisBatchResponse.getErrorType("WRONGTYPE wrong kind of value"))
        .isEqualTo("WRONGTYPE");
    assertThat(JedisBatchResponse.getErrorType("ERR unknown command")).isEqualTo("ERR");
    assertThat(JedisBatchResponse.getErrorType("NOAUTH")).isEqualTo("NOAUTH");
    assertThat(JedisBatchResponse.getErrorType("not a protocol error")).isNull();
    assertThat(JedisBatchResponse.getErrorType("")).isNull();
    assertThat(JedisBatchResponse.getErrorType(null)).isNull();
  }
}
