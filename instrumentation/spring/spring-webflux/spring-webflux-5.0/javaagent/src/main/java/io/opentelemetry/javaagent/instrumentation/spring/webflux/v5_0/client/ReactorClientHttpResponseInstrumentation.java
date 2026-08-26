/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.client;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.netty.handler.codec.http.HttpVersion;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.http.client.reactive.ClientHttpResponse;

class ReactorClientHttpResponseInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.http.client.reactive.ReactorClientHttpResponse");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(
                takesArgument(
                    0,
                    namedOneOf(
                        "reactor.ipc.netty.http.client.HttpClientResponse",
                        "reactor.netty.http.client.HttpClientResponse"))),
        getClass().getName() + "$ConstructorAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ClientHttpResponse clientHttpResponse,
        @Advice.Argument(0) Object reactorClientHttpResponse)
        throws Exception {
      String responseTypeName =
          reactorClientHttpResponse.getClass().getName().startsWith("reactor.ipc.")
              ? "reactor.ipc.netty.http.client.HttpClientResponse"
              : "reactor.netty.http.client.HttpClientResponse";
      Class<?> responseType =
          Class.forName(
              responseTypeName, false, reactorClientHttpResponse.getClass().getClassLoader());
      HttpVersion version =
          (HttpVersion) responseType.getMethod("version").invoke(reactorClientHttpResponse);
      HttpProtocolVersion.set(clientHttpResponse, version);
    }
  }
}
