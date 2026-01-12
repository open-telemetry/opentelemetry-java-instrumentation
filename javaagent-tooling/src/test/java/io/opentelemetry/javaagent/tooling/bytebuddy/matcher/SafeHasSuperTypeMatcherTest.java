/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperType;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.B;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.E;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.F;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.G;
import io.opentelemetry.javaagent.tooling.muzzle.AgentTooling;
import java.util.Iterator;
import java.util.stream.Stream;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.description.type.TypeList;
import net.bytebuddy.pool.TypePool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class SafeHasSuperTypeMatcherTest {

  private static TypePool typePool;

  @BeforeAll
  static void setUp() {
    typePool =
        AgentTooling.poolStrategy()
            .typePool(
                AgentTooling.locationStrategy()
                    .classFileLocator(SafeHasSuperTypeMatcherTest.class.getClassLoader(), null),
                SafeHasSuperTypeMatcherTest.class.getClassLoader());
  }

  private static Stream<Arguments> matcherParameters() {
    return Stream.of(
        Arguments.of(A.class, A.class, true),
        Arguments.of(A.class, B.class, true),
        Arguments.of(B.class, A.class, false),
        Arguments.of(A.class, E.class, true),
        Arguments.of(A.class, F.class, true),
        Arguments.of(B.class, G.class, true),
        Arguments.of(F.class, A.class, false),
        Arguments.of(F.class, F.class, true),
        Arguments.of(F.class, G.class, true));
  }

  @ParameterizedTest
  @MethodSource("matcherParameters")
  void testMatcher(Class<?> matcherClass, Class<?> typeClass, boolean expectedResult) {
    TypeDescription argument = typePool.describe(typeClass.getName()).resolve();

    boolean result = hasSuperType(named(matcherClass.getName())).matches(argument);

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
    verify(type, times(1)).asGenericType();
    verify(typeGeneric, times(1)).asErasure();
    verify(type, times(1)).getInterfaces();
    verify(type, times(1)).getSuperClass();
    verifyNoMoreInteractions(type, typeGeneric);
  }

  @Test
  @SuppressWarnings({"unchecked", "rawtypes"})
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
    verify(type, times(1)).getInterfaces();
    verify(interfaces, times(1)).iterator();
    verify(type, times(1)).asGenericType();
    verify(typeGeneric, times(1)).asErasure();
  }
}
