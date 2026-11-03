/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.instrument.Instrumentation;
import java.util.Iterator;
import org.junit.jupiter.api.Test;

class RedefinitionDiscoveryStrategyTest {

  @Test
  void classLoadersAreRetransformedInAnEarlierBatch() {
    Instrumentation instrumentation = mock(Instrumentation.class);
    when(instrumentation.getAllLoadedClasses())
        .thenReturn(
            new Class<?>[] {String.class, TestClassLoader.class, Object.class, ClassLoader.class});

    Iterator<Iterable<Class<?>>> batches =
        new AgentInstaller.RedefinitionDiscoveryStrategy().resolve(instrumentation).iterator();

    assertThat(batches.next()).containsExactly(TestClassLoader.class, ClassLoader.class);
    assertThat(batches.next()).containsExactly(String.class, Object.class);
    assertThat(batches.hasNext()).isFalse();
  }

  @Test
  void classesLoadedDuringRetransformationAreDiscovered() {
    Instrumentation instrumentation = mock(Instrumentation.class);
    when(instrumentation.getAllLoadedClasses())
        .thenReturn(new Class<?>[] {String.class, ClassLoader.class})
        .thenReturn(
            new Class<?>[] {String.class, TestClassLoader.class, Object.class, ClassLoader.class});

    Iterator<Iterable<Class<?>>> batches =
        new AgentInstaller.RedefinitionDiscoveryStrategy().resolve(instrumentation).iterator();

    assertThat(batches.next()).containsExactly(ClassLoader.class);
    assertThat(batches.next()).containsExactly(String.class);
    assertThat(batches.next()).containsExactly(TestClassLoader.class);
    assertThat(batches.next()).containsExactly(Object.class);
    assertThat(batches.hasNext()).isFalse();
  }

  private static class TestClassLoader extends ClassLoader {}
}
