/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_0.client;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.HttpClientRequest;
import io.vertx.core.http.impl.HttpClientImpl;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class HttpRequestImplInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.vertx.core.http.impl.HttpClientRequestImpl");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(2, String.class)).and(takesArgument(3, int.class)),
        HttpRequestImplInstrumentation.class.getName() + "$Vertx30Advice");
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArgument(1, boolean.class))
            .and(takesArgument(3, String.class))
            .and(takesArgument(4, int.class)),
        HttpRequestImplInstrumentation.class.getName() + "$Vertx35Advice");
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArgument(1, boolean.class))
            .and(takesArgument(4, String.class))
            .and(takesArgument(5, int.class)),
        HttpRequestImplInstrumentation.class.getName() + "$Vertx37Advice");
  }

  public static class Vertx30Advice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void attachRequestInfo(
        @Advice.This HttpClientRequest request,
        @Advice.Argument(0) HttpClientImpl client,
        @Advice.Argument(2) String host,
        @Advice.Argument(3) int port) {
      HttpClientOptions httpClientOptions =
          VirtualField.find(HttpClientImpl.class, HttpClientOptions.class).get(client);
      VirtualField.find(HttpClientRequest.class, VertxRequestInfo.class)
          .set(
              request,
              VertxRequestInfo.create(
                  httpClientOptions != null ? httpClientOptions.isSsl() : false, host, port));
    }
  }

  public static class Vertx35Advice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void attachRequestInfo(
        @Advice.This HttpClientRequest request,
        @Advice.Argument(1) boolean ssl,
        @Advice.Argument(3) String host,
        @Advice.Argument(4) int port) {
      VirtualField.find(HttpClientRequest.class, VertxRequestInfo.class)
          .set(request, VertxRequestInfo.create(ssl, host, port));
    }
  }

  public static class Vertx37Advice {
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void attachRequestInfo(
        @Advice.This HttpClientRequest request,
        @Advice.Argument(1) boolean ssl,
        @Advice.Argument(4) String host,
        @Advice.Argument(5) int port) {
      VirtualField.find(HttpClientRequest.class, VertxRequestInfo.class)
          .set(request, VertxRequestInfo.create(ssl, host, port));
    }
  }
}
