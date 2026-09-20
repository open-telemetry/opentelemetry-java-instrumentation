/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static io.opentelemetry.instrumentation.api.internal.SemconvStability.emitStableDatabaseSemconv;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisURI;
import io.lettuce.core.StatefulRedisConnectionImpl;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.output.StatusOutput;
import io.lettuce.core.protocol.Command;
import io.lettuce.core.protocol.CommandType;
import io.lettuce.core.protocol.DefaultEndpoint;
import io.lettuce.core.protocol.RedisCommand;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class LettuceAttributesGetterTest {

  private static final InetSocketAddress SELECTED_ADDRESS =
      InetSocketAddress.createUnresolved("selected-node", 6379);

  @Test
  void commandWithoutTargetUsesSelectedAddressOnlyForLegacySemconv() {
    RedisCommand<String, String, String> command = command();
    DefaultEndpoint endpoint = new DefaultEndpoint(ClientOptions.create());
    try {
      LettuceConnectionState.captureEndpoint(endpoint, SELECTED_ADDRESS, null, null);
      LettuceConnectionState.copy(endpoint, command, null);

      LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

      assertThat(getter.getServerAddress(command))
          .isEqualTo(emitStableDatabaseSemconv() ? null : "selected-node");
      assertThat(getter.getServerPort(command))
          .isEqualTo(emitStableDatabaseSemconv() ? null : 6379);
    } finally {
      endpoint.close();
    }
  }

  @Test
  void batchWithoutTargetUsesSelectedAddressOnlyForLegacySemconv() {
    LettuceBatchRequest request =
        LettuceBatchRequest.create(
            singletonList(command()), new LettuceConnectionState(SELECTED_ADDRESS, null, null));

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
            singletonList(command()),
            new LettuceConnectionState(SELECTED_ADDRESS, null, multipleSocketTarget));
    assertThat(getter.getServerAddress(multipleSocketRequest))
        .isEqualTo(emitStableDatabaseSemconv() ? null : "selected-node");
    assertThat(getter.getServerPort(multipleSocketRequest))
        .isEqualTo(emitStableDatabaseSemconv() ? null : 6379);

    LettuceBatchRequest singleSocketRequest =
        LettuceBatchRequest.create(
            singletonList(command()),
            new LettuceConnectionState(SELECTED_ADDRESS, null, singleSocketTarget));
    assertThat(getter.getServerAddress(singleSocketRequest))
        .isEqualTo(emitStableDatabaseSemconv() ? "/var/run/redis1.sock" : "selected-node");
    assertThat(getter.getServerPort(singleSocketRequest))
        .isEqualTo(emitStableDatabaseSemconv() ? null : 6379);
  }

  @Test
  void clusterEndpointWithoutTargetDoesNotUseSelectedRedisUri() {
    RedisURI selectedRedisUri = RedisURI.create("redis://selected-node:6379");
    RedisClusterClient client = RedisClusterClient.create(selectedRedisUri);
    DefaultEndpoint endpoint = new DefaultEndpoint(ClientOptions.create());
    try {
      Supplier<SocketAddress> addressSupplier = () -> SELECTED_ADDRESS;

      Object wrappedAddressSource =
          LettuceClusterClientInstrumentation.AttachEndpointAdvice.onEnter(
              client, new Object(), endpoint, selectedRedisUri, addressSupplier);

      assertThat(LettuceConnectionState.serverTarget(endpoint)).isNull();
      assertThat(wrappedAddressSource).isInstanceOf(Supplier.class);
      assertThat(((Supplier<?>) wrappedAddressSource).get()).isEqualTo(SELECTED_ADDRESS);
      assertThat(LettuceConnectionState.serverAddress(endpoint)).isEqualTo(SELECTED_ADDRESS);
    } finally {
      endpoint.close();
      client.shutdown(0, 15, SECONDS);
    }
  }

  @Test
  void asynchronousMasterReplicaTargetIsSetBeforeReturnedFutureCompletes() {
    RedisServerTarget target = LettuceServerTargets.of(RedisURI.create("redis://configured:6379"));
    StatefulRedisConnectionImpl<?, ?> connection = mock(StatefulRedisConnectionImpl.class);
    CompletableFuture<StatefulRedisConnectionImpl<?, ?>> originalFuture = new CompletableFuture<>();

    Object result =
        LettuceMasterSlaveInstrumentation.ConnectAdvice.onExit(
            new Object[] {target, null}, originalFuture);
    CompletableFuture<?> returnedFuture = (CompletableFuture<?>) result;

    assertThat(returnedFuture).isNotSameAs(originalFuture);
    originalFuture.complete(connection);
    assertThat(returnedFuture.join()).isSameAs(connection);

    RedisCommand<String, String, String> command = command();
    LettuceConnectionState.copy(connection, command);
    assertThat(LettuceConnectionState.serverTarget(command)).isSameAs(target);
  }

  @Test
  void cancellingAsynchronousMasterReplicaConnectionCancelsOriginalFuture() {
    CompletableFuture<StatefulRedisConnectionImpl<?, ?>> originalFuture = new CompletableFuture<>();

    Object result =
        LettuceMasterSlaveInstrumentation.ConnectAdvice.onExit(
            new Object[] {null, null}, originalFuture);
    CompletableFuture<?> returnedFuture = (CompletableFuture<?>) result;

    assertThat(returnedFuture.cancel(false)).isTrue();
    assertThat(returnedFuture).isCancelled();
    assertThat(originalFuture).isCancelled();
  }

  @Test
  void targetAttachmentFailureDoesNotFailAsynchronousMasterReplicaConnection() {
    RedisServerTarget initialTarget =
        LettuceServerTargets.of(RedisURI.create("redis://initial:6379"));
    RedisServerTarget failingTarget = mock(RedisServerTarget.class);
    when(failingTarget.getAddress()).thenThrow(new IllegalStateException("test"));
    StatefulRedisConnectionImpl<?, ?> connection = mock(StatefulRedisConnectionImpl.class);
    LettuceConnectionState.updateServerTarget(connection, initialTarget);
    CompletableFuture<StatefulRedisConnectionImpl<?, ?>> originalFuture = new CompletableFuture<>();

    CompletableFuture<?> returnedFuture =
        (CompletableFuture<?>)
            LettuceMasterSlaveInstrumentation.ConnectAdvice.onExit(
                new Object[] {failingTarget, null}, originalFuture);
    originalFuture.complete(connection);

    assertThat(returnedFuture.join()).isSameAs(connection);
  }

  @Test
  void cancellingAfterDelegateCompletionDoesNotCancelReturnedFuture() {
    DelayedCompletionFuture<StatefulRedisConnectionImpl<?, ?>> originalFuture =
        new DelayedCompletionFuture<>();
    StatefulRedisConnectionImpl<?, ?> connection = mock(StatefulRedisConnectionImpl.class);

    CompletableFuture<?> returnedFuture =
        (CompletableFuture<?>)
            LettuceMasterSlaveInstrumentation.ConnectAdvice.onExit(
                new Object[] {null, null}, originalFuture);
    originalFuture.complete(connection);

    assertThat(returnedFuture.cancel(false)).isFalse();
    originalFuture.runCompletion();
    assertThat(returnedFuture.join()).isSameAs(connection);
  }

  private static RedisCommand<String, String, String> command() {
    return new Command<>(CommandType.GET, new StatusOutput<>(StringCodec.UTF8));
  }

  private static class DelayedCompletionFuture<T> extends CompletableFuture<T> {
    private BiConsumer<? super T, ? super Throwable> completion;

    @Override
    public CompletableFuture<T> whenComplete(BiConsumer<? super T, ? super Throwable> completion) {
      this.completion = completion;
      return this;
    }

    void runCompletion() {
      completion.accept(join(), null);
    }
  }
}
