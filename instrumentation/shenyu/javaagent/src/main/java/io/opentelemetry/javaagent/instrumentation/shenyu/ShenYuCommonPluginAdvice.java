/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.shenyu;

import static io.opentelemetry.javaagent.instrumentation.shenyu.ShenYuPluginUtils.end;
import static io.opentelemetry.javaagent.instrumentation.shenyu.ShenYuPluginUtils.registerSpan;
import static io.opentelemetry.javaagent.instrumentation.shenyu.ShenYuSingletons.httpRouteGetter;
import static io.opentelemetry.javaagent.instrumentation.shenyu.ShenYuSingletons.instrumenter;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteHolder;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpRouteSource;
import net.bytebuddy.asm.Advice;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class ShenYuCommonPluginAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static void onEnter(
      @Advice.Argument(0) ServerWebExchange exchange,
      @Advice.This Object self,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {
    Context parentContext = Context.current();

    HttpRouteHolder.updateHttpRoute(
        parentContext, HttpRouteSource.CONTROLLER, httpRouteGetter(), exchange);

    if (!instrumenter().shouldStart(parentContext, self)) {
      return;
    }

    context = instrumenter().start(parentContext, self);
    scope = context.makeCurrent();

    registerSpan(exchange, context, self);
  }

  @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
  public static void onExit(
      @Advice.Argument(0) ServerWebExchange exchange,
      @Advice.Return(readOnly = false) Mono<Void> mono,
      @Advice.Thrown Throwable exception,
      @Advice.Local("otelContext") Context context,
      @Advice.Local("otelScope") Scope scope) {

    if (scope != null) {
      scope.close();
    }

    if (mono != null) {
      mono = end(mono, exchange);
    }
  }
}
