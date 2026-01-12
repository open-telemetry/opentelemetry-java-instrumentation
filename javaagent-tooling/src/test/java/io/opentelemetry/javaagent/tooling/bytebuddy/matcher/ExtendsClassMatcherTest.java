/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.extendsClass;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.B;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.F;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.G;
import io.opentelemetry.javaagent.tooling.muzzle.AgentTooling;
import java.util.stream.Stream;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.pool.TypePool;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.objectweb.asm.Opcodes;

class ExtendsClassMatcherTest {

  private static TypePool typePool;

  @BeforeAll
  static void setUp() {
    typePool =
        AgentTooling.poolStrategy()
            .typePool(
                AgentTooling.locationStrategy()
                    .classFileLocator(ExtendsClassMatcherTest.class.getClassLoader(), null),
                ExtendsClassMatcherTest.class.getClassLoader());
  }

  private static Stream<Arguments> matcherParameters() {
    return Stream.of(
        Arguments.of(A.class, B.class, false),
        Arguments.of(A.class, F.class, false),
        Arguments.of(G.class, F.class, false),
        Arguments.of(F.class, F.class, true),
        Arguments.of(F.class, G.class, true));
  }

  @ParameterizedTest
  @MethodSource("matcherParameters")
  void testMatcher(Class<?> matcherClass, Class<?> typeClass, boolean expectedResult) {
    TypeDescription argument = typePool.describe(typeClass.getName()).resolve();

    boolean result = extendsClass(named(matcherClass.getName())).matches(argument);

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  void testTraversalExceptions() {
    TypeDescription type = mock(TypeDescription.class);
    TypeDescription.Generic typeGeneric = mock(TypeDescription.Generic.class);

    when(type.getModifiers()).thenReturn(Opcodes.ACC_ABSTRACT);
    when(type.asGenericType()).thenReturn(typeGeneric);
    when(typeGeneric.asErasure()).thenThrow(new RuntimeException("asErasure exception"));
    when(type.getSuperClass()).thenThrow(new RuntimeException("getSuperClass exception"));

    boolean result = extendsClass(named(Object.class.getName())).matches(type);

    assertThat(result).isFalse();
    verify(type, times(1)).getModifiers();
    verify(type, times(1)).asGenericType();
    verify(typeGeneric, times(1)).asErasure();
    verify(type, times(1)).getSuperClass();
    verifyNoMoreInteractions(type, typeGeneric);
  }
}
