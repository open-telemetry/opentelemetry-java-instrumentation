/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.webflux.v5_0.server;

import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

public class DispatcherHandlerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.web.reactive.DispatcherHandler");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("handle"))
            .and(takesArgument(0, named("org.springframework.web.server.ServerWebExchange")))
            .and(takesArguments(1)),
        this.getClass().getName() + "$HandleAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("handleResult"))
            .and(takesArgument(0, named("org.springframework.web.server.ServerWebExchange"))),
        this.getClass().getName() + "$HandleResultAdvice");
  }

  @SuppressWarnings("unused")
  public static class HandleAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static Mono<Void> methodExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Argument(0) ServerWebExchange exchange,
        @Advice.Return Mono<Void> originalMono) {
      Mono<Void> mono = originalMono;
      if (mono != null) {
        // note: it seems like this code should go in @OnMethodExit of
        // HandlerAdapterInstrumentation.HandleAdvice instead, but for some reason "GET to bad
        // endpoint annotation API fail Mono" test fails with that placement
        mono = AdviceUtils.end(mono, exchange);
      }
      return mono;
    }
  }

  @SuppressWarnings("unused")
  public static class HandleResultAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static Mono<Void> methodExit(
        @Advice.Argument(0) ServerWebExchange exchange, @Advice.Return Mono<Void> mono) {
      return AdviceUtils.wrapMono(mono, exchange.getAttribute(AdviceUtils.CONTEXT));
    }
  }
}
