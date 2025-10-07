/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.bytebuddy.matcher;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasSuperMethod;
import static net.bytebuddy.matcher.ElementMatchers.isAnnotatedWith;
import static net.bytebuddy.matcher.ElementMatchers.none;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A;
import io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.Trace;
import net.bytebuddy.description.method.MethodDescription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HasSuperMethodMatcherTest {

  @ParameterizedTest
  @CsvSource({
    "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.A, a, false",
    "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.B, b, true",
    "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.C, c, false",
    "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.F, f, true",
    "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.G, g, false",
    "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.TracedClass, a, true",
    "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.UntracedClass, a, false",
    "io.opentelemetry.javaagent.tooling.bytebuddy.matcher.testclasses.UntracedClass, b, true"
  })
  void testMatcher(String className, String methodName, boolean expectedResult) throws Exception {
    Class<?> clazz = Class.forName(className);
    MethodDescription argument =
        new MethodDescription.ForLoadedMethod(clazz.getDeclaredMethod(methodName));

    boolean result = hasSuperMethod(isAnnotatedWith(Trace.class)).matches(argument);

    assertThat(result).isEqualTo(expectedResult);
  }

  @Test
  void testConstructorNeverMatches() {
    MethodDescription method = mock(MethodDescription.class);
    when(method.isConstructor()).thenReturn(true);

    boolean result = hasSuperMethod(none()).matches(method);

    assertThat(result).isFalse();
  }

  @Test
  void testTraversalExceptions() throws Exception {
    MethodDescription method = mock(MethodDescription.class);
    MethodDescription.SignatureToken sigToken =
        new MethodDescription.ForLoadedMethod(A.class.getDeclaredMethod("a")).asSignatureToken();

    when(method.isConstructor()).thenReturn(false);
    when(method.asSignatureToken()).thenReturn(sigToken);
    when(method.getDeclaringType()).thenReturn(null);

    boolean result = hasSuperMethod(none()).matches(method);

    assertThat(result).isFalse();
  }
}
