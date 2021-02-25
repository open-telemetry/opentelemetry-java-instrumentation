/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rmi.server;

import static io.opentelemetry.javaagent.instrumentation.api.rmi.ThreadLocalContext.THREAD_LOCAL_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.rmi.server.RmiServerTracer.tracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.isStatic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.lang.reflect.Method;
import java.rmi.server.RemoteServer;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class RmiServerInstrumentation extends InstrumentationModule {

  public RmiServerInstrumentation() {
    super("rmi", "rmi-server");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new ServerInstrumentation());
  }

  public static class ServerInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return extendsClass(named("java.rmi.server.RemoteServer"));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod().and(isPublic()).and(not(isStatic())),
          RmiServerInstrumentation.class.getName() + "$ServerAdvice");
    }
  }

  public static class ServerAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Origin Method method,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(RemoteServer.class);
      if (callDepth > 0) {
        return;
      }

      // TODO review and unify with all other SERVER instrumentation
      Context parentContext = THREAD_LOCAL_CONTEXT.getAndResetContext();

      context = tracer().startSpan(parentContext, method);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }
      scope.close();

      CallDepthThreadLocalMap.reset(RemoteServer.class);
      if (throwable != null) {
        RmiServerTracer.tracer().endExceptionally(context, throwable);
      } else {
        RmiServerTracer.tracer().end(context);
      }
    }
  }
}
