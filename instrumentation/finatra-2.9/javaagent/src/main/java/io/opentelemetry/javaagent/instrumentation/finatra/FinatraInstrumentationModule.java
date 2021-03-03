/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.finatra;

import static io.opentelemetry.javaagent.instrumentation.finatra.FinatraTracer.tracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.extendsClass;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.nameStartsWith;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import com.twitter.finagle.http.Response;
import com.twitter.finatra.http.contexts.RouteInfo;
import com.twitter.util.Future;
import com.twitter.util.FutureEventListener;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.tracer.ServerSpan;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.Some;

@AutoService(InstrumentationModule.class)
public class FinatraInstrumentationModule extends InstrumentationModule {
  public FinatraInstrumentationModule() {
    super("finatra", "finatra-2.9");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new RouteInstrumentation());
  }

  public static class RouteInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("com.twitter.finatra.http.internal.routing.Route");
    }

    @Override
    public ElementMatcher<? super TypeDescription> typeMatcher() {
      return nameStartsWith("com.twitter.finatra.")
          .<TypeDescription>and(
              extendsClass(named("com.twitter.finatra.http.internal.routing.Route")));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod()
              .and(named("handleMatch"))
              .and(takesArguments(2))
              .and(takesArgument(0, named("com.twitter.finagle.http.Request"))),
          FinatraInstrumentationModule.class.getName() + "$RouteAdvice");
    }
  }

  public static class RouteAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void nameSpan(
        @Advice.FieldValue("routeInfo") RouteInfo routeInfo,
        @Advice.FieldValue("clazz") Class<?> clazz,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      Context parentContext = Java8BytecodeBridge.currentContext();
      Span serverSpan = ServerSpan.fromContextOrNull(parentContext);
      if (serverSpan != null) {
        serverSpan.updateName(routeInfo.path());
      }

      context = tracer().startSpan(parentContext, clazz);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void setupCallback(
        @Advice.Thrown Throwable throwable,
        @Advice.Return Some<Future<Response>> responseOption,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {

      if (scope == null) {
        return;
      }

      if (throwable != null) {
        scope.close();
        tracer().endExceptionally(context, throwable);
        return;
      }

      responseOption.get().addEventListener(new Listener(context, scope));
    }
  }

  public static class Listener implements FutureEventListener<Response> {
    private final Context context;
    private final Scope scope;

    public Listener(Context context, Scope scope) {
      this.context = context;
      this.scope = scope;
    }

    @Override
    public void onSuccess(Response response) {
      scope.close();
      tracer().end(context);
    }

    @Override
    public void onFailure(Throwable cause) {
      scope.close();
      tracer().endExceptionally(context, cause);
    }
  }
}
