/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.v10_0.server.route;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.ArrayList;
import java.util.List;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.StubMethod;
import net.bytebuddy.matcher.ElementMatcher;
import org.junit.jupiter.api.Test;

class PathMatcherStaticInstrumentationTest {

  @Test
  void matchesSealedMatchingReturns() {
    ElementMatcher<? super MethodDescription> matcher = applyMatcher();
    TypeDescription matching =
        new ByteBuddy()
            .subclass(Object.class)
            .name("akka.http.scaladsl.server.PathMatcher$Matching")
            .make()
            .getTypeDescription();
    TypeDescription matched =
        new ByteBuddy()
            .subclass(matching)
            .name("akka.http.scaladsl.server.PathMatcher$Matched")
            .make()
            .getTypeDescription();
    TypeDescription unmatched =
        new ByteBuddy()
            .subclass(matching)
            .name("akka.http.scaladsl.server.PathMatcher$Unmatched$")
            .make()
            .getTypeDescription();

    // Remaining.apply declares Matched rather than Matching in Akka HTTP 10.0.0.
    assertThat(matcher.matches(applyMethod(matching))).isTrue();
    assertThat(matcher.matches(applyMethod(matched))).isTrue();
    assertThat(matcher.matches(applyMethod(unmatched))).isTrue();
  }

  private static ElementMatcher<? super MethodDescription> applyMatcher() {
    List<ElementMatcher<? super MethodDescription>> matchers = new ArrayList<>();
    TypeTransformer transformer = mock(TypeTransformer.class);
    doAnswer(
            invocation -> {
              matchers.add(invocation.getArgument(0));
              return null;
            })
        .when(transformer)
        .applyAdviceToMethod(any(), anyString());
    new PathMatcherStaticInstrumentation().transform(transformer);

    assertThat(matchers).hasSize(1);
    return matchers.get(0);
  }

  private static MethodDescription applyMethod(TypeDescription returnType) {
    TypeDescription path =
        new ByteBuddy()
            .subclass(Object.class)
            .name("akka.http.scaladsl.model.Uri$Path")
            .make()
            .getTypeDescription();
    return new ByteBuddy()
        .subclass(Object.class)
        .defineMethod("apply", returnType, Visibility.PUBLIC)
        .withParameters(path)
        .intercept(StubMethod.INSTANCE)
        .make()
        .getTypeDescription()
        .getDeclaredMethods()
        .filter(named("apply"))
        .getOnly();
  }
}
