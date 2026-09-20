/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.netty.buffer.ByteBuf;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;

@SuppressWarnings("InterruptedExceptionSwallowed")
class RedissonBatchStateTest {

  @ParameterizedTest
  @MethodSource("atomicFlags")
  void atomicFlag(Boolean options, boolean expected) {
    assertThat(RedissonBatchState.isAtomic(options)).isEqualTo(expected);
  }

  private static Stream<Arguments> atomicFlags() {
    return Stream.of(arguments(null, false), arguments(false, false), arguments(true, true));
  }

  @Test
  void legacyBatchOptions() throws ReflectiveOperationException {
    Class<?> type = batchOptionsClass();
    Method atomic;
    try {
      type.getMethod("isAtomic");
      atomic = type.getMethod("atomic");
    } catch (NoSuchMethodException ignored) {
      abort();
      return;
    }
    Object options = type.getMethod("defaults").invoke(null);
    assertThat(RedissonBatchState.isAtomic(options)).isFalse();

    atomic.invoke(options);
    assertThat(RedissonBatchState.isAtomic(options)).isTrue();
    assertThat(RedissonBatchState.isAtomic(type.getMethod("defaults").invoke(null))).isFalse();
  }

  @ParameterizedTest
  @CsvSource({
    "IN_MEMORY, false",
    "IN_MEMORY_ATOMIC, true",
    "REDIS_READ_ATOMIC, true",
    "REDIS_WRITE_ATOMIC, true"
  })
  void executionMode(String modeName, boolean expected) throws ReflectiveOperationException {
    Class<?> type = batchOptionsClass();
    Class<?> modeType;
    try {
      modeType = type.getMethod("getExecutionMode").getReturnType();
    } catch (NoSuchMethodException ignored) {
      abort();
      return;
    }
    Object options = type.getMethod("defaults").invoke(null);
    Object mode = modeType.getField(modeName).get(null);
    type.getMethod("executionMode", modeType).invoke(options, mode);

    assertThat(RedissonBatchState.isAtomic(options)).isEqualTo(expected);
    assertThat(RedissonBatchState.isAtomic(type.getMethod("defaults").invoke(null))).isFalse();
  }

  @Test
  void codecDecodingDoesNotBlockConcurrentEnqueue() throws Exception {
    RedissonBatchState state = new RedissonBatchState();
    RedisCommand<?> command = mock(RedisCommand.class);
    when(command.getName()).thenReturn("SET");
    Codec codec = mock(Codec.class, RETURNS_DEEP_STUBS);
    CountDownLatch decodingStarted = new CountDownLatch(1);
    CountDownLatch releaseDecoder = new CountDownLatch(1);
    when(codec.getValueDecoder().decode(any(), any()))
        .thenAnswer(
            invocation -> {
              decodingStarted.countDown();
              assertThat(releaseDecoder.await(10, SECONDS)).isTrue();
              return "value";
            });

    ExecutorService executor = Executors.newFixedThreadPool(2);
    Future<?> decoding =
        executor.submit(
            () ->
                state.add(
                    new Object(),
                    new Object(),
                    0,
                    command,
                    codec,
                    new Object[] {mock(ByteBuf.class)}));
    try {
      assertThat(decodingStarted.await(10, SECONDS)).isTrue();
      Future<?> concurrentEnqueue =
          executor.submit(
              () ->
                  state.add(new Object(), new Object(), 1, command, codec, new Object[] {"value"}));
      concurrentEnqueue.get(10, SECONDS);
    } finally {
      releaseDecoder.countDown();
      try {
        decoding.get(10, SECONDS);
      } finally {
        executor.shutdownNow();
      }
    }
  }

  @Test
  void codecDecodingDoesNotBlockFinish() throws Exception {
    RedissonBatchState state = new RedissonBatchState();
    RedisCommand<?> command = mock(RedisCommand.class);
    when(command.getName()).thenReturn("SET");
    Codec codec = mock(Codec.class, RETURNS_DEEP_STUBS);
    CountDownLatch decodingStarted = new CountDownLatch(1);
    CountDownLatch releaseDecoder = new CountDownLatch(1);
    when(codec.getValueDecoder().decode(any(), any()))
        .thenAnswer(
            invocation -> {
              decodingStarted.countDown();
              assertThat(releaseDecoder.await(10, SECONDS)).isTrue();
              return "value";
            });

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<?> decoding =
        executor.submit(
            () ->
                state.add(
                    new Object(),
                    new Object(),
                    0,
                    command,
                    codec,
                    new Object[] {mock(ByteBuf.class)}));
    try {
      assertThat(decodingStarted.await(10, SECONDS)).isTrue();
      state.add(new Object(), new Object(), 1, command, codec, new Object[] {"later"});

      RedissonBatchRequest request = state.finish(true);

      assertThat(request.getOperationName()).isEqualTo("MULTI SET");
      assertThat(request.getOperationBatchSize()).isEqualTo(2);
      assertThat(request.getQueryText()).isNull();
    } finally {
      releaseDecoder.countDown();
      try {
        decoding.get(10, SECONDS);
      } finally {
        executor.shutdownNow();
      }
    }
  }

  private static Class<?> batchOptionsClass() {
    try {
      return Class.forName("org.redisson.api.BatchOptions");
    } catch (ClassNotFoundException ignored) {
      return abort();
    }
  }
}
