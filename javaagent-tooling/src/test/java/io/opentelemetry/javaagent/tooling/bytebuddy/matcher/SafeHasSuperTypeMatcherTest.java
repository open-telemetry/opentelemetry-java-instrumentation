/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.tooling.muzzle.AgentTooling;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.pool.TypePool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Iterator;

@SuppressWarnings({"unchecked", "rawtypes"})
class SafeHasSuperTypeMatcherTest {

  private static TypePool typePool;

  @BeforeAll
  static void setUp() {
    typePool = AgentTooling.poolStrategy()
        .typePool(AgentTooling.locationStrategy().classFileLocator(SafeHasSuperTypeMatcherTest.class.getClassLoader(), null),
                  SafeHasSuperTypeMatcherTest.class.getClassLoader());
  }

  @ParameterizedTest
  @CsvSource({
      "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A, io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A, true",
      "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A, io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.B, true",
      "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.B, io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A, false",
      "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A, io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.E, true",
      "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A, io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.F, true",
      "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.B, io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.G, true",
      "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.F, io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A, false",
      "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.F, io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.F, true",
      "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.F, io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.G, true"
  })
  void testMatcher(String matcherClassName, String typeClassName, boolean expectedResult) {
    TypeDescription argument = typePool.describe(typeClassName).resolve();

    boolean result = hasSuperType(named(matcherClassName)).matches(argument);

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
      void testExceptionGettingInterfaces() {
    TypeDescription type = mock(TypeDescription.class);
    TypeDescription.Generic typeGeneric = mock(TypeDescription.Generic.class);

    when(type.asGenericType()).thenReturn(typeGeneric);
    when(typeGeneric.asErasure()).thenThrow(new RuntimeException("asErasure exception"));
    when(type.getInterfaces()).thenThrow(new RuntimeException("getInterfaces exception"));
    when(type.getSuperClass()).thenThrow(new RuntimeException("getSuperClass exception"));

    boolean result = hasSuperType(named(Object.class.getName())).matches(type);

    assertThat(result).isFalse();
    assertThatCode(() -> hasSuperType(named(Object.class.getName())).matches(type))
        .doesNotThrowAnyException();
  }

  @Test
  void testTraversalExceptions() {
    TypeDescription type = mock(TypeDescription.class);
    TypeDescription.Generic typeGeneric = mock(TypeDescription.Generic.class);
    TypeList.Generic interfaces = mock(TypeList.Generic.class);
    Iterator iterator = new ThrowOnFirstElement();

    when(type.getInterfaces()).thenReturn(interfaces);
    when(interfaces.iterator()).thenReturn(iterator);
    when(type.asGenericType()).thenReturn(typeGeneric);
    when(typeGeneric.asErasure()).thenThrow(new RuntimeException("asErasure exception"));

    boolean result = hasSuperType(named(Object.class.getName())).matches(type);

    assertThat(result).isFalse();
    assertThatCode(() -> hasSuperType(named(Object.class.getName())).matches(type))
        .doesNotThrowAnyException();
  }
}
