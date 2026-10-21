/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import com.lambdaworks.redis.RedisCommandExecutionException;
import com.lambdaworks.redis.RedisCommandTimeoutException;
import com.lambdaworks.redis.codec.Utf8StringCodec;
import com.lambdaworks.redis.output.StatusOutput;
import com.lambdaworks.redis.protocol.Command;
import com.lambdaworks.redis.protocol.CommandType;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.nio.ByteBuffer;
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
  void wirePrefixPreservesClientBufferAndMessage() {
    RedisCommand<String, String, String> command = command();
    ByteBuffer bytes = ByteBuffer.wrap("xéchec中 detailsy".getBytes(UTF_8));
    bytes.position(1);
    bytes.limit(bytes.capacity() - 1);
    String prefix = LettuceRedisErrorType.readPrefix(bytes);
    assertThat(prefix).isEqualTo("échec中");
    assertThat(bytes.position()).isEqualTo(1);
    assertThat(bytes.limit()).isEqualTo(bytes.capacity() - 1);

    command.getOutput().setError(bytes);
    String clientMessage = command.getOutput().getError();
    LettuceRedisErrorType.capture(command.getOutput(), prefix);
    Throwable error = new RedisCommandExecutionException(clientMessage);

    assertThat(getter.getErrorType(command, null, error)).isEqualTo("échec中");
    assertThat(error).hasMessage(clientMessage);
    assertThat(command.getOutput().getError()).isEqualTo(clientMessage);
    assertThat(getter.getErrorType(command, null, error))
        .isEqualTo(LettuceRedisErrorType.getErrorType(error));

    LettuceRedisErrorType.capture(command.getOutput(), prefix);
    assertThat(getter.getErrorType(command, null, new RedisCommandTimeoutException(clientMessage)))
        .isNull();
    LettuceRedisErrorType.capture(command.getOutput(), prefix);
    assertThat(
            getter.getErrorType(command, null, new RedisCommandExecutionException("OTHER details")))
        .isEqualTo("OTHER");
  }

  @Test
  void decoderScopeRestoresPreviousPrefixAndClearsAfterCompletion() {
    RedisCommand<String, String, String> command = command();
    LettuceRedisErrorType.ErrorDecode outer =
        LettuceRedisErrorType.startErrorDecode(ByteBuffer.wrap("échec中 details".getBytes(UTF_8)));
    LettuceRedisErrorType.ErrorDecode inner =
        LettuceRedisErrorType.startErrorDecode(ByteBuffer.wrap("OTHER details".getBytes(UTF_8)));
    LettuceRedisErrorType.endErrorDecode(command.getOutput(), inner);

    command.getOutput().setError("decoded details");
    LettuceRedisErrorType.captureDecoded(command.getOutput());
    Throwable error = new RedisCommandExecutionException("decoded details");
    assertThat(getter.getErrorType(command, null, error)).isEqualTo("échec中");
    LettuceRedisErrorType.endErrorDecode(command.getOutput(), outer);
    assertThat(getter.getErrorType(command, null, error)).isEqualTo("échec中");

    LettuceRedisErrorType.captureDecoded(command.getOutput());
    assertThat(getter.getErrorType(command, null, error)).isEqualTo("decoded");
  }

  private static RedisCommand<String, String, String> command() {
    Utf8StringCodec codec = new Utf8StringCodec();
    return new Command<>(CommandType.GET, new StatusOutput<>(codec));
  }
}
