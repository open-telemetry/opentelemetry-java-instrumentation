/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_0.client;

import static io.opentelemetry.javaagent.instrumentation.vertx.v3_0.client.VertxClientSingletons.HTTP_CLIENT_OPTIONS;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.impl.HttpClientImpl;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HttpClientImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.core.http.impl.HttpClientImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor(), HttpClientImplInstrumentation.class.getName() + "$AttachStateAdvice");
  }

  @SuppressWarnings("unused")
  public static class AttachStateAdvice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void attachHttpClientOptions(
        @Advice.This HttpClientImpl client,
        @Advice.FieldValue("options") HttpClientOptions options) {
      HTTP_CLIENT_OPTIONS.set(client, options);
    }
  }
}
