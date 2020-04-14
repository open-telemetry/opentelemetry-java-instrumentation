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
package io.opentelemetry.auto.instrumentation.akkahttp;

import static io.opentelemetry.auto.instrumentation.akkahttp.AkkaHttpClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.akkahttp.AkkaHttpClientDecorator.TRACER;
import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import akka.http.javadsl.model.headers.RawHeader;
import akka.http.scaladsl.HttpExt;
import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.auto.bootstrap.CallDepthThreadLocalMap;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.context.propagation.HttpTextFormat;
import io.opentelemetry.trace.Span;
import java.util.HashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import scala.concurrent.Future;
import scala.runtime.AbstractFunction1;
import scala.util.Try;

@Slf4j
@AutoService(Instrumenter.class)
public final class AkkaHttpClientInstrumentation extends Instrumenter.Default {
  public AkkaHttpClientInstrumentation() {
    super("akka-http", "akka-http-client");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("akka.http.scaladsl.HttpExt");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      AkkaHttpClientInstrumentation.class.getName() + "$OnCompleteHandler",
      AkkaHttpClientInstrumentation.class.getName() + "$AkkaHttpHeaders",
      packageName + ".AkkaHttpClientDecorator",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    // This is mainly for compatibility with 10.0
    transformers.put(
        named("singleRequest").and(takesArgument(0, named("akka.http.scaladsl.model.HttpRequest"))),
        AkkaHttpClientInstrumentation.class.getName() + "$SingleRequestAdvice");
    // This is for 10.1+
    transformers.put(
        named("singleRequestImpl")
            .and(takesArgument(0, named("akka.http.scaladsl.model.HttpRequest"))),
        AkkaHttpClientInstrumentation.class.getName() + "$SingleRequestAdvice");
    return transformers;
  }

  public static class SingleRequestAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope methodEnter(
        @Advice.Argument(value = 0, readOnly = false) HttpRequest request) {
      /*
      Versions 10.0 and 10.1 have slightly different structure that is hard to distinguish so here
      we cast 'wider net' and avoid instrumenting twice.
      In the future we may want to separate these, but since lots of code is reused we would need to come up
      with way of continuing to reusing it.
       */
      final int callDepth = CallDepthThreadLocalMap.incrementCallDepth(HttpExt.class);
      if (callDepth > 0) {
        return null;
      }

      final Span span =
          TRACER.spanBuilder(DECORATE.spanNameForRequest(request)).setSpanKind(CLIENT).startSpan();
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, request);

      final Context context = withSpan(span, Context.current());

      if (request != null) {
        final AkkaHttpHeaders headers = new AkkaHttpHeaders(request);
        OpenTelemetry.getPropagators().getHttpTextFormat().inject(context, request, headers);
        // Request is immutable, so we have to assign new value once we update headers
        request = headers.getRequest();
      }
      return new SpanWithScope(span, withScopedContext(context));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(0) final HttpRequest request,
        @Advice.This final HttpExt thiz,
        @Advice.Return final Future<HttpResponse> responseFuture,
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Thrown final Throwable throwable) {
      if (spanWithScope == null) {
        return;
      }
      CallDepthThreadLocalMap.reset(HttpExt.class);

      final Span span = spanWithScope.getSpan();

      if (throwable == null) {
        responseFuture.onComplete(new OnCompleteHandler(span), thiz.system().dispatcher());
      } else {
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
      }
      spanWithScope.closeScope();
    }
  }

  public static class OnCompleteHandler extends AbstractFunction1<Try<HttpResponse>, Void> {
    private final Span span;

    public OnCompleteHandler(final Span span) {
      this.span = span;
    }

    @Override
    public Void apply(final Try<HttpResponse> result) {
      if (result.isSuccess()) {
        DECORATE.onResponse(span, result.get());
      } else {
        DECORATE.onError(span, result.failed().get());
      }
      DECORATE.beforeFinish(span);
      span.end();
      return null;
    }
  }

  public static class AkkaHttpHeaders implements HttpTextFormat.Setter<HttpRequest> {
    private HttpRequest request;

    public AkkaHttpHeaders(final HttpRequest request) {
      this.request = request;
    }

    @Override
    public void set(final HttpRequest carrier, final String key, final String value) {
      // It looks like this cast is only needed in Java, Scala would have figured it out
      request = (HttpRequest) request.addHeader(RawHeader.create(key, value));
    }

    public HttpRequest getRequest() {
      return request;
    }
  }
}
