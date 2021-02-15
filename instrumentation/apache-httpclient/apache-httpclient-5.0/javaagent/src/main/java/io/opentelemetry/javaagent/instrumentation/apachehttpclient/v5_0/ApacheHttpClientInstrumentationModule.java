/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0.ApacheHttpClientTracer.tracer;
import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;

@AutoService(InstrumentationModule.class)
public class ApacheHttpClientInstrumentationModule extends InstrumentationModule {

  public ApacheHttpClientInstrumentationModule() {
    super("apache-httpclient", "apache-httpclient-5.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpClientInstrumentation());
  }

  public static class HttpClientInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("org.apache.hc.client5.http.classic.HttpClient");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return implementsInterface(named("org.apache.hc.client5.http.classic.HttpClient"));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
      // There are 8 execute(...) methods. Depending on the version, they may or may not delegate
      // to each other. Thus, all methods need to be instrumented. Because of argument position and
      // type, some methods can share the same advice class. The call depth tracking ensures only 1
      // span is created
      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(1))
              .and(takesArgument(0, named("org.apache.hc.core5.http.ClassicHttpRequest"))),
          ApacheHttpClientInstrumentationModule.class.getName() + "$RequestAdvice");

      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(2))
              .and(takesArgument(0, named("org.apache.hc.core5.http.ClassicHttpRequest")))
              .and(takesArgument(1, named("org.apache.hc.core5.http.protocol.HttpContext"))),
          ApacheHttpClientInstrumentationModule.class.getName() + "$RequestAdvice");

      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(2))
              .and(takesArgument(0, named("org.apache.hc.core5.http.HttpHost")))
              .and(takesArgument(1, named("org.apache.hc.core5.http.ClassicHttpRequest"))),
          ApacheHttpClientInstrumentationModule.class.getName() + "$RequestWithHostAdvice");

      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(3))
              .and(takesArgument(0, named("org.apache.hc.core5.http.HttpHost")))
              .and(takesArgument(1, named("org.apache.hc.core5.http.ClassicHttpRequest")))
              .and(takesArgument(2, named("org.apache.hc.core5.http.protocol.HttpContext"))),
          ApacheHttpClientInstrumentationModule.class.getName() + "$RequestWithHostAdvice");

      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(2))
              .and(takesArgument(0, named("org.apache.hc.core5.http.ClassicHttpRequest")))
              .and(
                  takesArgument(1, named("org.apache.hc.core5.http.io.HttpClientResponseHandler"))),
          ApacheHttpClientInstrumentationModule.class.getName() + "$RequestWithHandlerAdvice");

      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(3))
              .and(takesArgument(0, named("org.apache.hc.core5.http.ClassicHttpRequest")))
              .and(takesArgument(1, named("org.apache.hc.core5.http.protocol.HttpContext")))
              .and(
                  takesArgument(2, named("org.apache.hc.core5.http.io.HttpClientResponseHandler"))),
          ApacheHttpClientInstrumentationModule.class.getName()
              + "$RequestWithContextAndHandlerAdvice");

      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(3))
              .and(takesArgument(0, named("org.apache.hc.core5.http.HttpHost")))
              .and(takesArgument(1, named("org.apache.hc.core5.http.ClassicHttpRequest")))
              .and(
                  takesArgument(2, named("org.apache.hc.core5.http.io.HttpClientResponseHandler"))),
          ApacheHttpClientInstrumentationModule.class.getName()
              + "$RequestWithHostAndHandlerAdvice");

      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(4))
              .and(takesArgument(0, named("org.apache.hc.core5.http.HttpHost")))
              .and(takesArgument(1, named("org.apache.hc.core5.http.ClassicHttpRequest")))
              .and(takesArgument(2, named("org.apache.hc.core5.http.protocol.HttpContext")))
              .and(
                  takesArgument(3, named("org.apache.hc.core5.http.io.HttpClientResponseHandler"))),
          ApacheHttpClientInstrumentationModule.class.getName()
              + "$RequestWithHostAndContextAndHandlerAdvice");

      return transformers;
    }
  }

  public static class RequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) ClassicHttpRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      if (!tracer().shouldStartSpan(parentContext)) {
        return;
      }

      context = tracer().startSpan(parentContext, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
      ApacheHttpClientHelper.doMethodExit(context, result, throwable);
    }
  }

  public static class RequestWithHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) ClassicHttpRequest request,
        @Advice.Argument(value = 1, readOnly = false) HttpClientResponseHandler<?> handler,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      if (!tracer().shouldStartSpan(parentContext)) {
        return;
      }

      context = tracer().startSpan(parentContext, request);
      scope = context.makeCurrent();

      // Wrap the handler so we capture the status code
      if (handler != null) {
        handler = new WrappingStatusSettingResponseHandler(context, parentContext, handler);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
      ApacheHttpClientHelper.doMethodExit(context, result, throwable);
    }
  }

  public static class RequestWithContextAndHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) ClassicHttpRequest request,
        @Advice.Argument(value = 2, readOnly = false) HttpClientResponseHandler<?> handler,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      if (!tracer().shouldStartSpan(parentContext)) {
        return;
      }

      context = tracer().startSpan(parentContext, request);
      scope = context.makeCurrent();

      // Wrap the handler so we capture the status code
      if (handler != null) {
        handler = new WrappingStatusSettingResponseHandler(context, parentContext, handler);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
      ApacheHttpClientHelper.doMethodExit(context, result, throwable);
    }
  }

  public static class RequestWithHostAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) HttpHost host,
        @Advice.Argument(1) ClassicHttpRequest request,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      if (!tracer().shouldStartSpan(parentContext)) {
        return;
      }

      context = tracer().startSpan(parentContext, host, request);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
      ApacheHttpClientHelper.doMethodExit(context, result, throwable);
    }
  }

  public static class RequestWithHostAndHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) HttpHost host,
        @Advice.Argument(1) ClassicHttpRequest request,
        @Advice.Argument(value = 2, readOnly = false) HttpClientResponseHandler<?> handler,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      if (!tracer().shouldStartSpan(parentContext)) {
        return;
      }

      context = tracer().startSpan(parentContext, host, request);
      scope = context.makeCurrent();

      // Wrap the handler so we capture the status code
      if (handler != null) {
        handler = new WrappingStatusSettingResponseHandler(context, parentContext, handler);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
      ApacheHttpClientHelper.doMethodExit(context, result, throwable);
    }
  }

  public static class RequestWithHostAndContextAndHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void methodEnter(
        @Advice.Argument(0) HttpHost host,
        @Advice.Argument(1) ClassicHttpRequest request,
        @Advice.Argument(value = 3, readOnly = false) HttpClientResponseHandler<?> handler,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      Context parentContext = currentContext();
      if (!tracer().shouldStartSpan(parentContext)) {
        return;
      }

      context = tracer().startSpan(parentContext, host, request);
      scope = context.makeCurrent();

      // Wrap the handler so we capture the status code
      if (handler != null) {
        handler = new WrappingStatusSettingResponseHandler(context, parentContext, handler);
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      if (scope == null) {
        return;
      }

      scope.close();
      ApacheHttpClientHelper.doMethodExit(context, result, throwable);
    }
  }
}
