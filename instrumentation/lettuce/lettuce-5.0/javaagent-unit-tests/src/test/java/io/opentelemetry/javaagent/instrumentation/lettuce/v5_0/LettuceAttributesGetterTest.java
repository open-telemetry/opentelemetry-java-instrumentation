/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisURI;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.DefaultEndpoint;
import io.lettuce.core.protocol.RedisCommand;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.function.Supplier;
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
  void clusterEndpointWithoutTargetDoesNotUseSelectedRedisUri() {
    RedisURI selectedRedisUri = RedisURI.create("redis://selected-node:6379");
    RedisClusterClient client = RedisClusterClient.create(selectedRedisUri);
    DefaultEndpoint endpoint = new DefaultEndpoint(ClientOptions.create());
    try {
      Supplier<SocketAddress> addressSupplier = () -> SELECTED_ADDRESS;

      Supplier<SocketAddress> wrappedSupplier =
          LettuceClusterClientInstrumentation.AttachEndpointAdvice.onEnter(
              client, endpoint, selectedRedisUri, addressSupplier);

      assertThat(LettuceSingletons.ENDPOINT_TARGET.get(endpoint)).isNull();
      assertThat(wrappedSupplier.get()).isEqualTo(SELECTED_ADDRESS);
      assertThat(LettuceSingletons.ENDPOINT_ADDRESS.get(endpoint)).isEqualTo(SELECTED_ADDRESS);
    } finally {
      endpoint.close();
      client.shutdown(0, 15, SECONDS);
    }
  }

  private static RedisCommand<String, String, String> command() {
    return new Command<>(CommandType.GET, new StatusOutput<>(StringCodec.UTF8));
  }
}
