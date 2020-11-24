/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import static io.opentelemetry.javaagent.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static java.util.Collections.singletonList;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.javaagent.instrumentation.api.CallDepthThreadLocalMap;
import io.opentelemetry.javaagent.instrumentation.api.SpanWithScope;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpUriRequest;

@AutoService(InstrumentationModule.class)
public class ApacheHttpClientInstrumentationModule extends InstrumentationModule {

  public ApacheHttpClientInstrumentationModule() {
    super("apache-httpclient", "apache-httpclient-4.0");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new HttpClientInstrumentation());
  }

  private static final class HttpClientInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<ClassLoader> classLoaderOptimization() {
      return hasClassesNamed("org.apache.http.client.HttpClient");
    }

    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      return implementsInterface(named("org.apache.http.client.HttpClient"));
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
      // There are 8 execute(...) methods.  Depending on the version, they may or may not delegate
      // to eachother. Thus, all methods need to be instrumented.  Because of argument position and
      // type, some methods can share the same advice class.  The call depth tracking ensures only 1
      // span is created

      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(1))
              .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest"))),
          ApacheHttpClientInstrumentationModule.class.getName() + "$UriRequestAdvice");

      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(2))
              .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
              .and(takesArgument(1, named("org.apache.http.protocol.HttpContext"))),
          ApacheHttpClientInstrumentationModule.class.getName() + "$UriRequestAdvice");

      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(2))
              .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
              .and(takesArgument(1, named("org.apache.http.client.ResponseHandler"))),
          ApacheHttpClientInstrumentationModule.class.getName() + "$UriRequestWithHandlerAdvice");

      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(3))
              .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
              .and(takesArgument(1, named("org.apache.http.client.ResponseHandler")))
              .and(takesArgument(2, named("org.apache.http.protocol.HttpContext"))),
          ApacheHttpClientInstrumentationModule.class.getName() + "$UriRequestWithHandlerAdvice");

      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(2))
              .and(takesArgument(0, named("org.apache.http.HttpHost")))
              .and(takesArgument(1, named("org.apache.http.HttpRequest"))),
          ApacheHttpClientInstrumentationModule.class.getName() + "$RequestAdvice");

      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(3))
              .and(takesArgument(0, named("org.apache.http.HttpHost")))
              .and(takesArgument(1, named("org.apache.http.HttpRequest")))
              .and(takesArgument(2, named("org.apache.http.protocol.HttpContext"))),
          ApacheHttpClientInstrumentationModule.class.getName() + "$RequestAdvice");

      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(3))
              .and(takesArgument(0, named("org.apache.http.HttpHost")))
              .and(takesArgument(1, named("org.apache.http.HttpRequest")))
              .and(takesArgument(2, named("org.apache.http.client.ResponseHandler"))),
          ApacheHttpClientInstrumentationModule.class.getName() + "$RequestWithHandlerAdvice");

      transformers.put(
          isMethod()
              .and(named("execute"))
              .and(not(isAbstract()))
              .and(takesArguments(4))
              .and(takesArgument(0, named("org.apache.http.HttpHost")))
              .and(takesArgument(1, named("org.apache.http.HttpRequest")))
              .and(takesArgument(2, named("org.apache.http.client.ResponseHandler")))
              .and(takesArgument(3, named("org.apache.http.protocol.HttpContext"))),
          ApacheHttpClientInstrumentationModule.class.getName() + "$RequestWithHandlerAdvice");

      return transformers;
    }
  }

  public static class UriRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope methodEnter(@Advice.Argument(0) HttpUriRequest request) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }

      return ApacheHttpClientHelper.doMethodEnter(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter SpanWithScope spanWithScope,
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable) {
      ApacheHttpClientHelper.doMethodExitAndResetCallDepthThread(spanWithScope, result, throwable);
    }
  }

  public static class UriRequestWithHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope methodEnter(
        @Advice.Argument(0) HttpUriRequest request,
        @Advice.Argument(
                value = 1,
                optional = true,
                typing = Assigner.Typing.DYNAMIC,
                readOnly = false)
            Object handler) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }

      SpanWithScope spanWithScope = ApacheHttpClientHelper.doMethodEnter(request);

      // Wrap the handler so we capture the status code
      if (handler instanceof ResponseHandler) {
        handler =
            new WrappingStatusSettingResponseHandler(
                spanWithScope.getSpan(), (ResponseHandler) handler);
      }
      return spanWithScope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter SpanWithScope spanWithScope,
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable) {
      ApacheHttpClientHelper.doMethodExitAndResetCallDepthThread(spanWithScope, result, throwable);
    }
  }

  public static class RequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope methodEnter(
        @Advice.Argument(0) HttpHost host, @Advice.Argument(1) HttpRequest request) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }

      if (request instanceof HttpUriRequest) {
        return ApacheHttpClientHelper.doMethodEnter((HttpUriRequest) request);
      } else {
        return ApacheHttpClientHelper.doMethodEnter(
            new HostAndRequestAsHttpUriRequest(host, request));
      }
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter SpanWithScope spanWithScope,
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable) {
      ApacheHttpClientHelper.doMethodExitAndResetCallDepthThread(spanWithScope, result, throwable);
    }
  }

  public static class RequestWithHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope methodEnter(
        @Advice.Argument(0) HttpHost host,
        @Advice.Argument(1) HttpRequest request,
        @Advice.Argument(
                value = 2,
                optional = true,
                typing = Assigner.Typing.DYNAMIC,
                readOnly = false)
            Object handler) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }

      SpanWithScope spanWithScope;

      if (request instanceof HttpUriRequest) {
        spanWithScope = ApacheHttpClientHelper.doMethodEnter((HttpUriRequest) request);
      } else {
        spanWithScope =
            ApacheHttpClientHelper.doMethodEnter(new HostAndRequestAsHttpUriRequest(host, request));
      }

      // Wrap the handler so we capture the status code
      if (handler instanceof ResponseHandler) {
        handler =
            new WrappingStatusSettingResponseHandler(
                spanWithScope.getSpan(), (ResponseHandler) handler);
      }
      return spanWithScope;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter SpanWithScope spanWithScope,
        @Advice.Return Object result,
        @Advice.Thrown Throwable throwable) {
      ApacheHttpClientHelper.doMethodExitAndResetCallDepthThread(spanWithScope, result, throwable);
    }
  }
}
