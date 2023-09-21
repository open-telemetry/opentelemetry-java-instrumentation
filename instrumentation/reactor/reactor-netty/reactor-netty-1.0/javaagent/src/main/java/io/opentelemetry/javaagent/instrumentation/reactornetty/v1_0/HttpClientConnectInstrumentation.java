/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.reactornetty.v1_0;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientConfig;
import reactor.netty.http.client.HttpClientConfigBuddy;

public class HttpClientConnectInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("reactor.netty.http.client.HttpClientConnect");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("connect").and(returns(named("reactor.core.publisher.Mono"))),
        this.getClass().getName() + "$ConnectAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConnectAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Return(readOnly = false) Mono<? extends Connection> connection,
        @Advice.This HttpClient httpClient) {

      HttpClientConfig config = httpClient.configuration();
      // reactor-netty 1.0.x has a bug: the .mapConnect() function is not applied when deferred
      // configuration is used
      // we're fixing this bug here, so that our instrumentation can safely add its own
      // .mapConnect() listener
      if (HttpClientConfigBuddy.hasDeferredConfig(config)) {
        connection = HttpClientConfigBuddy.getConnector(config).apply(connection);
      }
    }
  }
}
