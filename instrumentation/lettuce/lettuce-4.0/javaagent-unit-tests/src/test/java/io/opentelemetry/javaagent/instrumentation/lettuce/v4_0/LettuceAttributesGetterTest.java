/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v4_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

import com.lambdaworks.redis.RedisURI;
import com.lambdaworks.redis.codec.Utf8StringCodec;
import com.lambdaworks.redis.output.StatusOutput;
import com.lambdaworks.redis.protocol.Command;
import com.lambdaworks.redis.protocol.CommandType;
import com.lambdaworks.redis.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.Test;

class LettuceAttributesGetterTest {

  private static final InetSocketAddress SELECTED_ADDRESS =
      InetSocketAddress.createUnresolved("selected-node", 6379);

  @Test
  void commandWithoutTargetUsesSelectedAddressOnlyForLegacySemconv() {
    RedisCommand<String, String, String> command = command();
    LettuceSingletons.COMMAND_ADDRESS.set(command, SELECTED_ADDRESS);

    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

    assertThat(getter.getServerAddress(command))
        .isEqualTo(emitStableDatabaseSemconv() ? null : "selected-node");
    assertThat(getter.getServerPort(command)).isEqualTo(emitStableDatabaseSemconv() ? null : 6379);
  }

  @Test
  void batchWithoutTargetUsesSelectedAddressOnlyForLegacySemconv() {
    LettuceBatchRequest request =
        LettuceBatchRequest.create(singletonList(command()), SELECTED_ADDRESS, null, null);

    LettuceBatchAttributesGetter getter = new LettuceBatchAttributesGetter();

    assertThat(getter.getServerAddress(request))
        .isEqualTo(emitStableDatabaseSemconv() ? null : "selected-node");
    assertThat(getter.getServerPort(request)).isEqualTo(emitStableDatabaseSemconv() ? null : 6379);
  }

  @Test
  void unixSocketTargetsControlStableBatchAttributes() {
    RedisServerTarget multipleSocketTarget =
        LettuceServerTargets.ofUris(
            asList(
                RedisURI.Builder.socket("/var/run/redis1.sock").build(),
                RedisURI.Builder.socket("/var/run/redis2.sock").build()));
    RedisServerTarget singleSocketTarget =
        LettuceServerTargets.ofUris(
            singletonList(RedisURI.Builder.socket("/var/run/redis1.sock").build()));
    LettuceBatchAttributesGetter getter = new LettuceBatchAttributesGetter();

    LettuceBatchRequest multipleSocketRequest =
        LettuceBatchRequest.create(
            singletonList(command()), SELECTED_ADDRESS, null, multipleSocketTarget);
    assertThat(getter.getServerAddress(multipleSocketRequest))
        .isEqualTo(emitStableDatabaseSemconv() ? null : "selected-node");
    assertThat(getter.getServerPort(multipleSocketRequest))
        .isEqualTo(emitStableDatabaseSemconv() ? null : 6379);

    LettuceBatchRequest singleSocketRequest =
        LettuceBatchRequest.create(
            singletonList(command()), SELECTED_ADDRESS, null, singleSocketTarget);
    assertThat(getter.getServerAddress(singleSocketRequest))
        .isEqualTo(emitStableDatabaseSemconv() ? "/var/run/redis1.sock" : "selected-node");
    assertThat(getter.getServerPort(singleSocketRequest))
        .isEqualTo(emitStableDatabaseSemconv() ? null : 6379);
  }

  private static RedisCommand<String, String, String> command() {
    Utf8StringCodec codec = new Utf8StringCodec();
    return new Command<>(CommandType.GET, new StatusOutput<>(codec));
  }
}
