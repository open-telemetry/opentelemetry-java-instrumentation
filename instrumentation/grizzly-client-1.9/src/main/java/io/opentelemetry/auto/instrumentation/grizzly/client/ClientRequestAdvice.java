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

package io.opentelemetry.auto.instrumentation.grizzly.client;

import static io.opentelemetry.auto.instrumentation.grizzly.client.ClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.grizzly.client.ClientDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.grizzly.client.InjectAdapter.SETTER;
import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.getSpan;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;

import com.ning.http.client.AsyncHandler;
import com.ning.http.client.Request;
import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.InstrumentationContext;
import io.opentelemetry.instrumentation.api.Pair;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;

public class ClientRequestAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope onEnter(
      @Advice.Argument(0) final Request request,
      @Advice.Argument(1) final AsyncHandler<?> handler) {
    Context parentContext = Context.current();

    Span span =
        TRACER
            .spanBuilder(DECORATE.spanNameForRequest(request))
            .setSpanKind(CLIENT)
            .setParent(getSpan(parentContext))
            .startSpan();

    DECORATE.afterStart(span);
    DECORATE.onRequest(span, request);

    InstrumentationContext.get(AsyncHandler.class, Pair.class)
        .put(handler, Pair.of(parentContext, span));

    Context newContext = withSpan(span, parentContext);
    OpenTelemetry.getPropagators().getHttpTextFormat().inject(newContext, request, SETTER);
    return withScopedContext(newContext);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.Enter final Scope scope) {
    // span closed in ClientResponseAdvice
    scope.close();
  }
}
