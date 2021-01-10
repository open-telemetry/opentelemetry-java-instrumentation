/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.undertow;

import static io.opentelemetry.javaagent.instrumentation.undertow.UndertowHttpServerTracer.tracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import io.undertow.server.HttpServerExchange;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class UndertowInstrumentationModule extends InstrumentationModule {

  public UndertowInstrumentationModule() {
    super("undertow", "undertow-2.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HandlerInstrumentation());
  }

  public static class HandlerInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher.Junction<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("io.undertow.server.HttpHandler");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return implementsInterface(named("io.undertow.server.HttpHandler"));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          named("handleRequest")
              .and(takesArgument(0, named("io.undertow.server.HttpServerExchange")))
              .and(isPublic()),
          UndertowHandlerAdvice.class.getName());
    }

    public static class UndertowHandlerAdvice {
      @Advice.OnMethodEnter(suppress = Throwable.class)
      public static void onEnter(
          @Advice.Origin Method method,
          @Advice.Argument(value = 0, readOnly = false) HttpServerExchange exchange,
          @Advice.Local("otelScope") Scope scope) {
        Context attachedContext = tracer().getServerContext(exchange);
        if (attachedContext != null) {
          if (!Java8BytecodeBridge.currentContext().equals(attachedContext)) {
            scope = attachedContext.makeCurrent();
          }
          return;
        }

        Context context = tracer().startServerSpan(exchange, method);
        scope = context.makeCurrent();

        exchange.addExchangeCompleteListener(new EndSpanListener(context));
      }

      @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
      public static void onExit(
          @Advice.Argument(value = 0, readOnly = false) HttpServerExchange exchange,
          @Advice.Local("otelScope") Scope scope) {
        if (scope == null) {
          return;
        }

        scope.close();
        // span is closed by EndSpanListener
      }
    }
  }
}
