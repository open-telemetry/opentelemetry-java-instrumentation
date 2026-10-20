/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static java.util.Collections.nCopies;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.lambdaworks.redis.RedisCommandExecutionException;
import com.lambdaworks.redis.RedisCommandTimeoutException;
import com.lambdaworks.redis.RedisConnectionException;
import com.lambdaworks.redis.codec.Utf8StringCodec;
import com.lambdaworks.redis.output.StatusOutput;
import com.lambdaworks.redis.protocol.Command;
import com.lambdaworks.redis.protocol.CommandType;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.Test;

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

  @Test
  void errorTypeUsesRedisReplyPrefixForCommandAndBatch() {
    RedisCommand<String, String, String> command = command();
    LettuceBatchRequest batch = LettuceBatchRequest.create(singletonList(command), null, null);
    RedisCommandExecutionException error =
        new RedisCommandExecutionException("WRONGTYPE Operation against a key");

    assertThat(getter.getErrorType(command, null, error)).isEqualTo("WRONGTYPE");
    assertThat(new LettuceBatchAttributesGetter().getErrorType(batch, null, error))
        .isEqualTo("WRONGTYPE");
  }

  @Test
  void errorTypePreservesFallbackForClientAndUnstructuredErrors() {
    RedisCommand<String, String, String> command = command();
    LettuceBatchRequest batch = LettuceBatchRequest.create(singletonList(command), null, null);
    LettuceBatchAttributesGetter batchGetter = new LettuceBatchAttributesGetter();

    assertThat(getter.getErrorType(command, null, null)).isNull();
    assertThat(batchGetter.getErrorType(batch, null, null)).isNull();
    assertThat(getter.getErrorType(command, null, new RedisCommandExecutionException("not a code")))
        .isNull();
    assertThat(batchGetter.getErrorType(batch, null, new RedisCommandExecutionException(" ")))
        .isNull();
    assertThat(
            getter.getErrorType(
                command, null, new RedisCommandTimeoutException("WRONGTYPE command timed out")))
        .isNull();
    assertThat(
            batchGetter.getErrorType(
                batch, null, new RedisCommandTimeoutException("WRONGTYPE command timed out")))
        .isNull();
    assertThat(
            getter.getErrorType(
                command, null, new RedisConnectionException("WRONGTYPE connection closed")))
        .isNull();
    assertThat(
            batchGetter.getErrorType(
                batch, null, new RedisConnectionException("WRONGTYPE connection closed")))
        .isNull();
    assertThat(getter.getErrorType(command, null, new CancellationException("WRONGTYPE canceled")))
        .isNull();
    assertThat(
            batchGetter.getErrorType(batch, null, new CancellationException("WRONGTYPE canceled")))
        .isNull();
    assertThat(getter.getErrorType(command, null, new IllegalStateException("WRONGTYPE client")))
        .isNull();
    assertThat(batchGetter.getErrorType(batch, null, new IllegalStateException("WRONGTYPE client")))
        .isNull();
    assertThat(
            getter.getErrorType(command, null, new RedisCommandExecutionException((String) null)))
        .isNull();
    assertThat(
            batchGetter.getErrorType(
                batch, null, new RedisCommandExecutionException(String.join("", nCopies(65, "A")))))
        .isNull();
  }

  private static RedisCommand<String, String, String> command() {
    Utf8StringCodec codec = new Utf8StringCodec();
    return new Command<>(CommandType.GET, new StatusOutput<>(codec));
  }
}
