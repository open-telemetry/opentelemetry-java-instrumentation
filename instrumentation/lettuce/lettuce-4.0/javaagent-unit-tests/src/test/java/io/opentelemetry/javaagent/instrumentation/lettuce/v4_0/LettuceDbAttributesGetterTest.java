/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static java.util.Collections.nCopies;
import static org.assertj.core.api.Assertions.assertThat;

import com.lambdaworks.redis.RedisCommandExecutionException;
import com.lambdaworks.redis.RedisCommandTimeoutException;
import com.lambdaworks.redis.codec.Utf8StringCodec;
import com.lambdaworks.redis.output.StatusOutput;
import com.lambdaworks.redis.protocol.Command;
import com.lambdaworks.redis.protocol.CommandType;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LettuceDbAttributesGetterTest {

  private final LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

  @Test
  void commandUsesConfiguredTarget() {
    RedisCommand<String, String, String> command = command();
    LettuceServerTargets.capture(command, RedisServerTarget.ofEndpoint("configured-node:6379"));

    assertThat(getter.getServerAddress(command)).isEqualTo("configured-node");
    assertThat(getter.getServerPort(command)).isEqualTo(null);
  }

  @Test
  void commandWithoutConfiguredTargetOmitsServerAttributes() {
    RedisCommand<String, String, String> command = command();

    assertThat(getter.getServerAddress(command)).isEqualTo(null);
    assertThat(getter.getServerPort(command)).isEqualTo(null);
  }

  @ParameterizedTest
  @MethodSource("errorTypes")
  void errorType(Throwable error, String expected) {
    assertThat(LettuceRedisErrorType.getErrorType(error)).isEqualTo(expected);
  }

  private static Stream<Arguments> errorTypes() {
    return Stream.of(
        Arguments.of(
            new RedisCommandExecutionException("WRONGTYPE Operation against a key"), "WRONGTYPE"),
        Arguments.of(null, null),
        Arguments.of(new RedisCommandTimeoutException("WRONGTYPE command timed out"), null),
        Arguments.of(new RedisCommandExecutionException("wrongtype Operation against a key"), null),
        Arguments.of(new RedisCommandExecutionException(" "), null),
        Arguments.of(new RedisCommandExecutionException((String) null), null),
        Arguments.of(new RedisCommandExecutionException(String.join("", nCopies(65, "A"))), null));
  }

  private static RedisCommand<String, String, String> command() {
    Utf8StringCodec codec = new Utf8StringCodec();
    return new Command<>(CommandType.GET, new StatusOutput<>(codec));
  }
}
