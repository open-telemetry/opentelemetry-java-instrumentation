/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.lettuce.v5_0;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;
import static java.util.Collections.nCopies;
import static java.util.Collections.singletonList;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.RedisCommandExecutionException;
import io.lettuce.core.RedisCommandTimeoutException;
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
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiConsumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class LettuceAttributesGetterTest {

  @Test
  void commandWithoutTargetOmitsServerAttributes() {
    RedisCommand<String, String, String> command = command();
    try (DefaultEndpoint endpoint = new DefaultEndpoint(ClientOptions.create())) {
      LettuceConnectionState.captureEndpoint(endpoint, null, null);
      LettuceConnectionState.copy(endpoint, command, null);

      LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();

      assertThat(getter.getServerAddress(command)).isEqualTo(null);
      assertThat(getter.getServerPort(command)).isEqualTo(null);
    }
  }

  @Test
  void batchWithoutTargetOmitsServerAttributes() {
    LettuceBatchRequest request =
        LettuceBatchRequest.create(
            singletonList(command()), new LettuceConnectionState(null, null));

    LettuceBatchAttributesGetter getter = new LettuceBatchAttributesGetter();

    assertThat(getter.getServerAddress(request)).isEqualTo(null);
    assertThat(getter.getServerPort(request)).isEqualTo(null);
  }

  @ParameterizedTest
  @MethodSource("errorTypes")
  void errorType(Throwable error, String expected) {
    assertThat(LettuceRedisErrorType.getErrorType(error)).isEqualTo(expected);
  }

  private static Stream<Arguments> errorTypes() {
    String longPrefix = String.join("", nCopies(65, "A"));
    return Stream.of(
        Arguments.of(
            new RedisCommandExecutionException("WRONGTYPE Operation against a key"), "WRONGTYPE"),
        Arguments.of(new RedisCommandExecutionException("ERR unknown command"), "ERR"),
        Arguments.of(new RedisCommandExecutionException("Custom.Code! details"), "Custom.Code!"),
        Arguments.of(new RedisCommandExecutionException("lowercase details"), "lowercase"),
        Arguments.of(new RedisCommandExecutionException(longPrefix + " details"), longPrefix),
        Arguments.of(new RedisCommandExecutionException("Custom.Code!"), "Custom.Code!"),
        Arguments.of(new RedisCommandExecutionException("  Custom.Code! details"), "Custom.Code!"),
        Arguments.of(new RedisCommandExecutionException("Custom\tCode! details"), "Custom\tCode!"),
        Arguments.of(
            new RedisCommandExecutionException("  Custom.Code!\r\nsuffix details"),
            "Custom.Code!\r\nsuffix"),
        Arguments.of(new RedisCommandExecutionException("échec details"), "échec"),
        Arguments.of(new RedisCommandExecutionException("\u00a0CODE details"), "\u00a0CODE"),
        Arguments.of(null, null),
        Arguments.of(new RedisCommandTimeoutException("WRONGTYPE command timed out"), null),
        Arguments.of(new RedisCommandExecutionException(""), null),
        Arguments.of(new RedisCommandExecutionException(" "), null),
        Arguments.of(new RedisCommandExecutionException("\r\n"), "\r\n"),
        Arguments.of(new RedisCommandExecutionException((String) null), null),
        Arguments.of(new RedisCommandExecutionException("\t"), "\t"));
  }

  @Test
  void wirePrefixPreservesClientBufferAndMessage() {
    RedisCommand<String, String, String> command = command();
    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();
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
    LettuceDbAttributesGetter getter = new LettuceDbAttributesGetter();
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

  @Test
  void batchErrorUsesRequestOrderUnlessClientFails() {
    Throwable first = new RedisCommandExecutionException("");
    Throwable second = new RedisCommandExecutionException("Second.Code! details");
    Throwable client = new RedisCommandTimeoutException("WRONGTYPE client timeout");

    assertThat(LettuceBatchContext.getError(null, new Throwable[] {null, first, second}))
        .isSameAs(first);
    assertThat(LettuceBatchContext.getError(null, new Throwable[] {first, client}))
        .isSameAs(client);
    assertThat(LettuceBatchContext.getError(null, new Throwable[] {client, first}))
        .isSameAs(client);
    assertThat(LettuceBatchContext.getError(second, new Throwable[] {first})).isSameAs(second);
    assertThat(LettuceBatchContext.getError(client, new Throwable[] {first})).isSameAs(client);
    assertThat(LettuceBatchContext.getError(null, new Throwable[] {null, null})).isNull();
  }

  @Test
  void unixSocketTargetsControlBatchAttributes() {
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
            singletonList(command()), new LettuceConnectionState(null, multipleSocketTarget));
    assertThat(getter.getServerAddress(multipleSocketRequest)).isEqualTo(null);
    assertThat(getter.getServerPort(multipleSocketRequest)).isEqualTo(null);

    LettuceBatchRequest singleSocketRequest =
        LettuceBatchRequest.create(
            singletonList(command()), new LettuceConnectionState(null, singleSocketTarget));
    assertThat(getter.getServerAddress(singleSocketRequest)).isEqualTo("/var/run/redis1.sock");
    assertThat(getter.getServerPort(singleSocketRequest)).isEqualTo(null);
  }

  @Test
  void clusterEndpointWithoutTargetDoesNotUseSelectedRedisUri() {
    RedisURI selectedRedisUri = RedisURI.create("redis://selected-node:6379");
    RedisClusterClient client = RedisClusterClient.create(selectedRedisUri);
    try (DefaultEndpoint endpoint = new DefaultEndpoint(ClientOptions.create())) {
      LettuceClusterClientInstrumentation.AttachEndpointAdvice.onEnter(
          client, new Object(), endpoint, selectedRedisUri);

      assertThat(LettuceConnectionState.serverTarget(endpoint)).isNull();
      RedisCommand<String, String, String> command = command();
      LettuceConnectionState.copy(endpoint, command, null);
      assertThat(LettuceConnectionState.databaseIndex(command)).isEqualTo(0);
    } finally {
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
