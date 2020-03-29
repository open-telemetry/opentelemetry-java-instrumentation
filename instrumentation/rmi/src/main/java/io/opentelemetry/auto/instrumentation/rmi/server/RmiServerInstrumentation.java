/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.rmi.server;

import static io.opentelemetry.auto.bootstrap.instrumentation.rmi.ThreadLocalContext.THREAD_LOCAL_CONTEXT;
import static io.opentelemetry.auto.instrumentation.rmi.server.RmiServerDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.rmi.server.RmiServerDecorator.TRACER;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.trace.Span.Kind.SERVER;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import java.lang.reflect.Method;
import java.rmi.server.RemoteServer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class RmiServerInstrumentation extends Instrumenter.Default {

  public RmiServerInstrumentation() {
    super("rmi", "rmi-server");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {packageName + ".RmiServerDecorator"};
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return extendsClass(named("java.rmi.server.RemoteServer"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod().and(isPublic()).and(not(isStatic())), getClass().getName() + "$ServerAdvice");
  }

  public static class ServerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = true)
    public static SpanWithScope onEnter(
        @Advice.This final Object thiz, @Advice.Origin final Method method) {
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(RemoteServer.class);
      if (callDepth > 0) {
        return null;
      }
      final SpanContext context = THREAD_LOCAL_CONTEXT.getAndResetContext();

      final Span.Builder spanBuilder =
          TRACER.spanBuilder(DECORATE.spanNameForMethod(method)).setSpanKind(SERVER);
      if (context != null) {
        spanBuilder.setParent(context);
      } else {
        spanBuilder.setNoParent();
      }
      final Span span = spanBuilder.startSpan();
      span.setAttribute("span.origin.type", thiz.getClass().getCanonicalName());

      DECORATE.afterStart(span);
      return new SpanWithScope(span, currentContextWith(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }
      CallDepthThreadLocalMap.reset(RemoteServer.class);

      final Span span = spanWithScope.getSpan();
      DECORATE.onError(span, throwable);
      span.end();

      spanWithScope.closeScope();
    }
  }
}
