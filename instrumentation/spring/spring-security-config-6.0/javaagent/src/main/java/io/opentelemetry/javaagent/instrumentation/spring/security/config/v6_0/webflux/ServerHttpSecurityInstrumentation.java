/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0.webflux;

import static io.opentelemetry.javaagent.instrumentation.spring.security.config.v6_0.EnduserAttributesCapturerSingletons.enduserAttributesCapturer;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.spring.security.config.v6_0.webflux.EnduserAttributesServerHttpSecurityCustomizer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.security.config.web.server.ServerHttpSecurity;

/** Instrumentation for {@link ServerHttpSecurity}. */
public class ServerHttpSecurityInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.security.config.web.server.ServerHttpSecurity");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod().and(isPublic()).and(named("build")).and(takesArguments(0)),
        getClass().getName() + "$BuildAdvice");
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.This ServerHttpSecurity serverHttpSecurity) {
      new EnduserAttributesServerHttpSecurityCustomizer(enduserAttributesCapturer())
          .customize(serverHttpSecurity);
    }
  }
}
