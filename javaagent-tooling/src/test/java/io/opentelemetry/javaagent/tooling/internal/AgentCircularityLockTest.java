/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.internal;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;

import java.lang.instrument.ClassFileTransformer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.dynamic.ClassFileLocator;
import net.bytebuddy.utility.JavaModule;
import org.junit.jupiter.api.Test;

class AgentCircularityLockTest {

  @Test
  void derivedTransformersShareLock() throws Exception {
    AgentCircularityLock lock = AgentCircularityLock.INSTANCE;
    AtomicInteger discoveries = new AtomicInteger();
    AgentBuilder builder =
        new AgentBuilder.Default()
            .with(lock)
            .with(
                new AgentBuilder.Listener.Adapter() {
                  @Override
                  public void onDiscovery(
                      String typeName, ClassLoader classLoader, JavaModule module, boolean loaded) {
                    assertThat(lock.isHeldByCurrentThread()).isTrue();
                    discoveries.incrementAndGet();
                  }
                });
    ClassFileTransformer early = builder.with(AgentBuilder.RedefinitionStrategy.DISABLED).makeRaw();
    ClassFileTransformer main =
        builder.with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION).makeRaw();
    byte[] bytes = ClassFileLocator.ForClassLoader.read(AgentCircularityLockTest.class);
    String name = AgentCircularityLockTest.class.getName().replace('.', '/');
    ClassLoader loader = getClass().getClassLoader();
    early.transform(loader, name, null, null, bytes);
    main.transform(loader, name, null, null, bytes);
    assertThat(discoveries.get()).isEqualTo(2);
    assertThat(lock.isHeldByCurrentThread()).isFalse();

    assertThat(lock.acquire()).isTrue();
    try {
      early.transform(loader, name, null, null, bytes);
      main.transform(loader, name, null, null, bytes);
      assertThat(discoveries.get()).isEqualTo(2);
    } finally {
      lock.release();
    }
  }

  @Test
  void reportsOwnershipOnlyForCurrentThread() throws Exception {
    AgentCircularityLock lock = AgentCircularityLock.INSTANCE;
    assertThat(lock.isHeldByCurrentThread()).isFalse();
    assertThat(lock.acquire()).isTrue();
    try {
      assertThat(lock.isHeldByCurrentThread()).isTrue();
      assertThat(lock.acquire()).isFalse();
      CompletableFuture.runAsync(
              () -> {
                assertThat(lock.isHeldByCurrentThread()).isFalse();
                assertThat(lock.acquire()).isTrue();
                try {
                  assertThat(lock.isHeldByCurrentThread()).isTrue();
                } finally {
                  lock.release();
                }
                assertThat(lock.isHeldByCurrentThread()).isFalse();
              })
          .get(10, SECONDS);
      assertThat(lock.isHeldByCurrentThread()).isTrue();
    } finally {
      lock.release();
    }
    assertThat(lock.isHeldByCurrentThread()).isFalse();
  }
}
