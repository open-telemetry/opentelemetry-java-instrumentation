/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static org.assertj.core.api.Assertions.assertThat;

import com.lambdaworks.redis.codec.Utf8StringCodec;
import com.lambdaworks.redis.output.StatusOutput;
import com.lambdaworks.redis.protocol.Command;
import com.lambdaworks.redis.protocol.CommandType;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class LettuceDbAttributesGetterTest {

  private static final InetSocketAddress SELECTED_ADDRESS =
      InetSocketAddress.createUnresolved("selected-node", 6380);

  private final LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

  @Test
  void commandUsesConfiguredTargetOnlyForStableSemconv() {
    RedisCommand<String, String, String> command = command();
    LettuceSingletons.COMMAND_ADDRESS.set(command, SELECTED_ADDRESS);
    LettuceServerTargets.capture(
        command, RedisServerTarget.ofEndpoint("configured-node:6379"));

    assertThat(getter.getServerAddress(command))
        .isEqualTo(emitStableDatabaseSemconv() ? "configured-node" : "selected-node");
    assertThat(getter.getServerPort(command)).isEqualTo(emitStableDatabaseSemconv() ? null : 6380);
  }

  @Test
  void commandWithoutConfiguredTargetOmitsStableServerAttributes() {
    RedisCommand<String, String, String> command = command();
    LettuceSingletons.COMMAND_ADDRESS.set(command, SELECTED_ADDRESS);

    assertThat(getter.getServerAddress(command))
        .isEqualTo(emitStableDatabaseSemconv() ? null : "selected-node");
    assertThat(getter.getServerPort(command)).isEqualTo(emitStableDatabaseSemconv() ? null : 6380);
  }

  private static RedisCommand<String, String, String> command() {
    Utf8StringCodec codec = new Utf8StringCodec();
    return new Command<>(CommandType.GET, new StatusOutput<>(codec));
  }
}
