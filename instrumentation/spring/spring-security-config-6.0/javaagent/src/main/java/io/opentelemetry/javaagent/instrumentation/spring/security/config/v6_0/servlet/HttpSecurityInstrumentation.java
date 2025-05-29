/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0.servlet;

import static io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturerSingletons.enduserAttributesCapturer;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isProtected;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.servlet.EnduserAttributesHttpSecurityCustomizer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;

/** Instrumentation for {@link HttpSecurity}. */
public class HttpSecurityInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.security.config.annotation.web.builders.HttpSecurity");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isProtected()).and(named("performBuild")).and(takesArguments(0)),
        getClass().getName() + "$PerformBuildAdvice");
  }

  @SuppressWarnings("unused")
  public static class PerformBuildAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This HttpSecurity httpSecurity) {
      new EnduserAttributesHttpSecurityCustomizer(enduserAttributesCapturer())
          .customize(httpSecurity);
    }
  }
}
