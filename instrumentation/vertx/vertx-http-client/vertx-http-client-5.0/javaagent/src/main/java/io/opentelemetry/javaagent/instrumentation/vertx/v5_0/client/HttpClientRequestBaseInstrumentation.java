/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v5_0.client;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.http.impl.HttpClientRequestBase;
import io.vertx.core.net.HostAndPort;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HttpClientRequestBaseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.core.http.impl.HttpClientRequestBase");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), this.getClass().getName() + "$ConstructorAdvice");

    transformer.applyAdviceToMethod(
        named("authority").and(takesArgument(0, named("io.vertx.core.net.HostAndPort"))),
        this.getClass().getName() + "$SetAuthorityAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This HttpClientRequestBase request,
        @Advice.FieldValue("authority") HostAndPort authority) {
      VertxClientSingletons.setAuthority(request, authority);
    }
  }

  @SuppressWarnings("unused")
  public static class SetAuthorityAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This HttpClientRequestBase request, @Advice.Argument(0) HostAndPort authority) {
      VertxClientSingletons.setAuthority(request, authority);
    }
  }
}
