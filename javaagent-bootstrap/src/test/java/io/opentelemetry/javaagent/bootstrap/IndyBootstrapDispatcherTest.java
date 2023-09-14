/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import org.junit.jupiter.api.Test;

public class IndyBootstrapDispatcherTest {

  @Test
  void testVoidNoopMethodHandle() throws Throwable {
    MethodHandle noArg = generateAndCheck(MethodType.methodType(void.class));
    noArg.invokeExact();

    MethodHandle intArg = generateAndCheck(MethodType.methodType(void.class, int.class));
    intArg.invokeExact(42);
  }

  @Test
  void testIntNoopMethodHandle() throws Throwable {
    MethodHandle noArg = generateAndCheck(MethodType.methodType(int.class));
    assertThat((int) noArg.invokeExact()).isEqualTo(0);

    MethodHandle intArg = generateAndCheck(MethodType.methodType(int.class, int.class));
    assertThat((int) intArg.invokeExact(42)).isEqualTo(0);
  }

  @Test
  void testBooleanNoopMethodHandle() throws Throwable {
    MethodHandle noArg = generateAndCheck(MethodType.methodType(boolean.class));
    assertThat((boolean) noArg.invokeExact()).isEqualTo(false);

    MethodHandle intArg = generateAndCheck(MethodType.methodType(boolean.class, int.class));
    assertThat((boolean) intArg.invokeExact(42)).isEqualTo(false);
  }

  @Test
  void testReferenceNoopMethodHandle() throws Throwable {
    MethodHandle noArg = generateAndCheck(MethodType.methodType(Runnable.class));
    assertThat((Runnable) noArg.invokeExact()).isEqualTo(null);

    MethodHandle intArg = generateAndCheck(MethodType.methodType(Runnable.class, int.class));
    assertThat((Runnable) intArg.invokeExact(42)).isEqualTo(null);
  }

  private static MethodHandle generateAndCheck(MethodType type) {
    MethodHandle mh = IndyBootstrapDispatcher.generateNoopMethodHandle(type);
    assertThat(mh.type()).isEqualTo(type);
    return mh;
  }
}
