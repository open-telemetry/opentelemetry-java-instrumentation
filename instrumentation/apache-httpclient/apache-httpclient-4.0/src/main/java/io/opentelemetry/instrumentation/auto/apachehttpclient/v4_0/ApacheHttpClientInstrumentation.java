/*
 * Copyright The OpenTelemetry Authors
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

package io.opentelemetry.instrumentation.auto.apachehttpclient.v4_0;

import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isAbstract;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.auto.api.CallDepthThreadLocalMap;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import java.util.HashMap;
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

@AutoService(Instrumenter.class)
public class ApacheHttpClientInstrumentation extends Instrumenter.Default {

  public ApacheHttpClientInstrumentation() {
    super("httpclient", "apache-httpclient", "apache-http-client");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("org.apache.http.client.HttpClient");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("org.apache.http.client.HttpClient"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.instrumentation.auto.apachehttpclient.v4_0.ApacheHttpClientTracer",
      "io.opentelemetry.instrumentation.auto.apachehttpclient.v4_0.HttpHeadersInjectAdapter",
      "io.opentelemetry.instrumentation.auto.apachehttpclient.v4_0.HostAndRequestAsHttpUriRequest",
      "io.opentelemetry.instrumentation.auto.apachehttpclient.v4_0.ApacheHttpClientHelper",
      "io.opentelemetry.instrumentation.auto.apachehttpclient.v4_0.WrappingStatusSettingResponseHandler",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    // There are 8 execute(...) methods.  Depending on the version, they may or may not delegate to
    // eachother. Thus, all methods need to be instrumented.  Because of argument position and type,
    // some methods can share the same advice class.  The call depth tracking ensures only 1 span is
    // created

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(1))
            .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest"))),
        ApacheHttpClientInstrumentation.class.getName() + "$UriRequestAdvice");

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
            .and(takesArgument(1, named("org.apache.http.protocol.HttpContext"))),
        ApacheHttpClientInstrumentation.class.getName() + "$UriRequestAdvice");

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
            .and(takesArgument(1, named("org.apache.http.client.ResponseHandler"))),
        ApacheHttpClientInstrumentation.class.getName() + "$UriRequestWithHandlerAdvice");

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.http.client.methods.HttpUriRequest")))
            .and(takesArgument(1, named("org.apache.http.client.ResponseHandler")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext"))),
        ApacheHttpClientInstrumentation.class.getName() + "$UriRequestWithHandlerAdvice");

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(2))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest"))),
        ApacheHttpClientInstrumentation.class.getName() + "$RequestAdvice");

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest")))
            .and(takesArgument(2, named("org.apache.http.protocol.HttpContext"))),
        ApacheHttpClientInstrumentation.class.getName() + "$RequestAdvice");

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(3))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest")))
            .and(takesArgument(2, named("org.apache.http.client.ResponseHandler"))),
        ApacheHttpClientInstrumentation.class.getName() + "$RequestWithHandlerAdvice");

    transformers.put(
        isMethod()
            .and(named("execute"))
            .and(not(isAbstract()))
            .and(takesArguments(4))
            .and(takesArgument(0, named("org.apache.http.HttpHost")))
            .and(takesArgument(1, named("org.apache.http.HttpRequest")))
            .and(takesArgument(2, named("org.apache.http.client.ResponseHandler")))
            .and(takesArgument(3, named("org.apache.http.protocol.HttpContext"))),
        ApacheHttpClientInstrumentation.class.getName() + "$RequestWithHandlerAdvice");

    return transformers;
  }

  public static class UriRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope methodEnter(@Advice.Argument(0) final HttpUriRequest request) {
      int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpClient.class);
      if (callDepth > 0) {
        return null;
      }

      return ApacheHttpClientHelper.doMethodEnter(request);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {
      ApacheHttpClientHelper.doMethodExitAndResetCallDepthThread(spanWithScope, result, throwable);
    }
  }

  public static class UriRequestWithHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope methodEnter(
        @Advice.Argument(0) final HttpUriRequest request,
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
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {
      ApacheHttpClientHelper.doMethodExitAndResetCallDepthThread(spanWithScope, result, throwable);
    }
  }

  public static class RequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope methodEnter(
        @Advice.Argument(0) final HttpHost host, @Advice.Argument(1) final HttpRequest request) {
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
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {
      ApacheHttpClientHelper.doMethodExitAndResetCallDepthThread(spanWithScope, result, throwable);
    }
  }

  public static class RequestWithHandlerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope methodEnter(
        @Advice.Argument(0) final HttpHost host,
        @Advice.Argument(1) final HttpRequest request,
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
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Return final Object result,
        @Advice.Thrown final Throwable throwable) {
      ApacheHttpClientHelper.doMethodExitAndResetCallDepthThread(spanWithScope, result, throwable);
    }
  }
}
