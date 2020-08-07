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

import static io.opentelemetry.auto.instrumentation.grizzly.client.GrizzlyClientTracer.TRACER;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHandler;
import com.ning.http.client.Response;
import io.grpc.Context;
import io.opentelemetry.auto.bootstrap.ContextStore;
import io.opentelemetry.auto.bootstrap.InstrumentationContext;
import io.opentelemetry.auto.bootstrap.instrumentation.api.Pair;
import io.opentelemetry.context.ContextUtils;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import net.bytebuddy.asm.Advice;

public class GrizzlyClientResponseAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope onEnter(
      @Advice.This final AsyncCompletionHandler<?> handler,
      @Advice.Argument(0) final Response response) {

    // TODO I think all this should happen on exit, not on enter.
    // After response was handled by user provided handler.
    ContextStore<AsyncHandler, Pair> contextStore =
        InstrumentationContext.get(AsyncHandler.class, Pair.class);
    Pair<Context, Span> spanWithParent = contextStore.get(handler);
    if (null != spanWithParent) {
      contextStore.put(handler, null);
    }
    if (spanWithParent.hasRight()) {
      TRACER.end(spanWithParent.getRight(), response);
    }
    return spanWithParent.hasLeft()
        ? ContextUtils.withScopedContext(spanWithParent.getLeft())
        : null;
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.Enter final Scope scope) {
    if (null != scope) {
      scope.close();
    }
  }
}
