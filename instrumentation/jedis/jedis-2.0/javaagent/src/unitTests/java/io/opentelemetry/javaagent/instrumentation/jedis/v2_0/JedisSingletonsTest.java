/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v2_0;

import static java.util.Arrays.asList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.instrumentation.jedis.common.v1_4.JedisBatchResponse;
import io.opentelemetry.javaagent.instrumentation.jedis.common.v1_4.JedisRedisErrorType;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import redis.clients.jedis.Connection;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Protocol;
import redis.clients.jedis.exceptions.JedisDataException;

class JedisSingletonsTest {

  @Test
  void invalidUpdatesClearCapturedConnectionTarget() {
    Connection connection = new Connection("localhost", 6380);

    JedisSingletons.captureConnectionTarget(connection);
    assertThat(JedisSingletons.connectionTarget(connection)).isNotNull();

    connection.setHost(null);
    JedisSingletons.captureConnectionTarget(connection);
    assertThat(JedisSingletons.connectionTarget(connection)).isNull();

    connection.setHost("localhost");
    JedisSingletons.captureConnectionTarget(connection);
    assertThat(JedisSingletons.connectionTarget(connection)).isNotNull();

    connection.setPort(65536);
    JedisSingletons.captureConnectionTarget(connection);
    assertThat(JedisSingletons.connectionTarget(connection)).isNull();
  }

  @Test
  void unavailableConfiguredTargetSuppressesConnectionFallback() {
    Connection connection = new Connection("localhost", 6380);
    Context context = JedisSingletons.configuredTargetContext(null);

    try (Scope ignored = context.makeCurrent()) {
      JedisSingletons.captureConnectionTarget(connection);
      assertThat(JedisSingletons.connectionTarget(connection)).isNull();
    }
  }

  @Test
  void poolPreservesUnavailableConfiguredTarget() {
    JedisPool pool = new JedisPool("localhost", 6379);
    try {
      assertThat(JedisSingletons.configuredPoolTargetContext(pool)).isNull();

      JedisSingletons.setPoolTarget(pool, null);
      Context context = JedisSingletons.configuredPoolTargetContext(pool);
      assertThat(context).isNotNull();

      Connection connection = new Connection("localhost", 6380);
      try (Scope ignored = context.makeCurrent()) {
        JedisSingletons.captureConnectionTarget(connection);
        assertThat(JedisSingletons.connectionTarget(connection)).isNull();
      }
    } finally {
      pool.destroy();
    }
  }

  @Test
  void borrowedPoolResourceReplacesPreviouslyCapturedTarget() {
    JedisPool pool = new JedisPool("localhost", 6379);
    Jedis jedis = new Jedis("selected", 6380);
    try {
      JedisSingletons.captureConnectionTarget(jedis.getClient());
      assertThat(JedisSingletons.connectionTarget(jedis.getClient()).getAddress())
          .isEqualTo("selected");

      JedisSingletons.setPoolTarget(pool, RedisServerTarget.ofHostAndPort("configured", 6381));
      Context context = JedisSingletons.configuredPoolTargetContext(pool);
      assertThat(context).isNotNull();

      try (Scope ignored = context.makeCurrent()) {
        JedisSingletons.captureJedisTarget(jedis);
      }

      RedisServerTarget target = JedisSingletons.connectionTarget(jedis.getClient());
      assertThat(target.getAddress()).isEqualTo("configured");
      assertThat(target.getPort()).isEqualTo(6381);
    } finally {
      jedis.disconnect();
      pool.destroy();
    }
  }

  @Test
  void terminalExceptionTakesPrecedenceOverCapturedBatchError() {
    Connection connection = new Connection("localhost", 6380);
    Context context = JedisBatchResponse.store(Context.root(), connection, 2);

    try (Scope ignored = context.makeCurrent()) {
      JedisBatchResponse.capture(
          connection,
          asList(
              new JedisDataException("WRONGTYPE expected"),
              new JedisDataException("WRONGTYPE expected")));
    }

    JedisBatchResponse response = requireNonNull(JedisBatchResponse.getResponse(context));
    assertThat(response.isFailed()).isTrue();
    assertThat(response.getErrorType()).isEqualTo("WRONGTYPE");
    assertThat(
            new JedisDbAttributesGetter()
                .getErrorType(
                    JedisRequest.create(connection, Protocol.Command.PING),
                    response,
                    new IOException("connection closed")))
        .isEqualTo(IOException.class.getName());
    assertThat(JedisRedisErrorType.getErrorType(new JedisDataException("UNKNOWNCODE details")))
        .isEqualTo(JedisDataException.class.getName());
  }
}
