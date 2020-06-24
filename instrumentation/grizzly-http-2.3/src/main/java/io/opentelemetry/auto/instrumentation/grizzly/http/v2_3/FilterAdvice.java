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
package io.opentelemetry.auto.instrumentation.grizzly.http.v2_3;

import static io.opentelemetry.auto.instrumentation.grizzly.http.v2_3.GrizzlyDecorator.TRACER;
import static io.opentelemetry.context.ContextUtils.withScopedContext;

import io.grpc.Context;
import io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerTracer;
import io.opentelemetry.context.Scope;
import net.bytebuddy.asm.Advice;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;

public class FilterAdvice {

  @Advice.OnMethodEnter(suppress = Throwable.class)
  public static Scope onEnter(
      @Advice.This final BaseFilter it, @Advice.Argument(0) final FilterChainContext ctx) {
    if (TRACER.getCurrentSpan().getContext().isValid()) {
      return null;
    }
    final Context context =
        (Context) ctx.getAttributes().getAttribute(HttpServerTracer.CONTEXT_ATTRIBUTE);
    if (context == null) {
      return null;
    }
    return withScopedContext(context);
  }

  @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
  public static void onExit(@Advice.This final BaseFilter it, @Advice.Enter final Scope scope) {
    if (scope != null) {
      scope.close();
    }
  }
}
