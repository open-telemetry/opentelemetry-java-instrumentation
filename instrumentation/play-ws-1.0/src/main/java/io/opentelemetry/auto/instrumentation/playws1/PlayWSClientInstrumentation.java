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
package io.opentelemetry.auto.instrumentation.playws1;

import static io.opentelemetry.auto.instrumentation.playws1.HeadersInjectAdapter.SETTER;
import static io.opentelemetry.auto.instrumentation.playws1.PlayWSClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.playws1.PlayWSClientDecorator.TRACER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.classLoaderHasNoResources;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import play.shaded.ahc.org.asynchttpclient.AsyncHandler;
import play.shaded.ahc.org.asynchttpclient.Request;

@AutoService(Instrumenter.class)
public class PlayWSClientInstrumentation extends Instrumenter.Default {
  public PlayWSClientInstrumentation() {
    super("play-ws");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return not(
        classLoaderHasNoResources("play/shaded/ahc/org/asynchttpclient/AsyncHttpClient.class"));
  }

  @Override
  public ElementMatcher<? super TypeDescription> typeMatcher() {
    // CachingAsyncHttpClient rejects overrides to AsyncHandler
    // It also delegates to another AsyncHttpClient
    return implementsInterface(named("play.shaded.ahc.org.asynchttpclient.AsyncHttpClient"))
        .and(not(named("play.api.libs.ws.ahc.cache.CachingAsyncHttpClient")));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("execute"))
            .and(takesArguments(2))
            .and(takesArgument(0, named("play.shaded.ahc.org.asynchttpclient.Request")))
            .and(takesArgument(1, named("play.shaded.ahc.org.asynchttpclient.AsyncHandler"))),
        PlayWSClientInstrumentation.class.getName() + "$ClientAdvice");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "io.opentelemetry.auto.decorator.BaseDecorator",
      "io.opentelemetry.auto.decorator.ClientDecorator",
      "io.opentelemetry.auto.decorator.HttpClientDecorator",
      packageName + ".PlayWSClientDecorator",
      packageName + ".HeadersInjectAdapter",
      packageName + ".AsyncHandlerWrapper"
    };
  }

  public static class ClientAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Span methodEnter(
        @Advice.Argument(0) final Request request,
        @Advice.Argument(value = 1, readOnly = false) AsyncHandler asyncHandler) {

      final Span span = TRACER.spanBuilder("play-ws.request").setSpanKind(CLIENT).startSpan();

      DECORATE.afterStart(span);
      DECORATE.onRequest(span, request);
      TRACER.getHttpTextFormat().inject(span.getContext(), request, SETTER);

      asyncHandler = new AsyncHandlerWrapper(asyncHandler, span);

      return span;
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Enter final Span clientSpan, @Advice.Thrown final Throwable throwable) {

      if (throwable != null) {
        DECORATE.onError(clientSpan, throwable);
        DECORATE.beforeFinish(clientSpan);
        clientSpan.end();
      }
    }
  }
}
