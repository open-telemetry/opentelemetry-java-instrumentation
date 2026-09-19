/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.redisson.common.v3_0;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.abort;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.reflect.Method;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

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

  private static Class<?> batchOptionsClass() {
    try {
      return Class.forName("org.redisson.api.BatchOptions");
    } catch (ClassNotFoundException ignored) {
      return abort();
    }
  }
}
