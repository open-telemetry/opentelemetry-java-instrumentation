/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.gwt;

import static io.opentelemetry.javaagent.extension.matcher.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.instrumentation.gwt.GwtTracer.tracer;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.InstrumentationModule;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(InstrumentationModule.class)
public class GwtInstrumentationModule extends InstrumentationModule {

  public GwtInstrumentationModule() {
    super("gwt", "gwt-2.0");
  }

  @Override
  public ElementMatcher.Junction<ClassLoader> classLoaderMatcher() {
    // class added in gwt 2.0
    return hasClassesNamed("com.google.gwt.uibinder.client.UiBinder");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RpcInstrumentation());
  }

  public static class RpcInstrumentation implements TypeInstrumentation {

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return named("com.google.gwt.user.server.rpc.RPC");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<MethodDescription>, String> transformers = new HashMap<>();

      transformers.put(
          named("invokeAndEncodeResponse")
              .and(takesArguments(5))
              .and(takesArgument(0, Object.class))
              .and(takesArgument(1, Method.class))
              .and(takesArgument(2, Object[].class))
              .and(takesArgument(3, named("com.google.gwt.user.server.rpc.SerializationPolicy")))
              .and(takesArgument(4, int.class)),
          RpcInstrumentation.class.getName() + "$RpcInvokeAdvice");

      // encodeResponseForFailure is called by invokeAndEncodeResponse in case of failure
      transformers.put(
          named("encodeResponseForFailure")
              .and(takesArguments(4))
              .and(takesArgument(0, Method.class))
              .and(takesArgument(1, Throwable.class))
              .and(takesArgument(2, named("com.google.gwt.user.server.rpc.SerializationPolicy")))
              .and(takesArgument(3, int.class)),
          RpcInstrumentation.class.getName() + "$RpcFailureAdvice");

      return transformers;
    }

    public static class RpcInvokeAdvice {
      @Advice.OnMethodEnter(suppress = Throwable.class)
      public static void onEnter(
          @Advice.Argument(0) Object target,
          @Advice.Argument(1) Method method,
          @Advice.Local("otelContext") Context context,
          @Advice.Local("otelScope") Scope scope) {

        context = tracer().startRpcSpan(target, method);
        scope = context.makeCurrent();
      }

      @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
      public static void onExit(
          @Advice.Thrown Throwable throwable,
          @Advice.Local("otelContext") Context context,
          @Advice.Local("otelScope") Scope scope) {
        scope.close();

        tracer().endSpan(context, throwable);
      }
    }

    public static class RpcFailureAdvice {
      @Advice.OnMethodEnter(suppress = Throwable.class)
      public static void onEnter(@Advice.Argument(1) Throwable throwable) {
        if (throwable == null) {
          return;
        }
        tracer().rpcFailure(throwable);
      }
    }
  }
}
