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
package io.opentelemetry.auto.instrumentation.grizzly;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.HttpServerDecorator.SPAN_ATTRIBUTE;
import static io.opentelemetry.auto.instrumentation.grizzly.GrizzlyHttpServerTracer.TRACER;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.instrumentation.api.SpanWithScope;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.lang.reflect.Method;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.glassfish.grizzly.http.server.AfterServiceListener;
import org.glassfish.grizzly.http.server.Request;
import org.glassfish.grizzly.http.server.Response;

@AutoService(Instrumenter.class)
public class GrizzlyHttpHandlerInstrumentation extends Instrumenter.Default {

  public GrizzlyHttpHandlerInstrumentation() {
    super("grizzly");
  }

  @Override
  protected boolean defaultEnabled() {
    return false;
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.glassfish.grizzly.http.server.HttpHandler");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".GrizzlyHttpServerTracer",
      packageName + ".GrizzlyRequestExtractAdapter",
      getClass().getName() + "$SpanClosingListener"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("doHandle"))
            .and(takesArgument(0, named("org.glassfish.grizzly.http.server.Request")))
            .and(takesArgument(1, named("org.glassfish.grizzly.http.server.Response"))),
        GrizzlyHttpHandlerInstrumentation.class.getName() + "$HandleAdvice");
  }

  public static class HandleAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope methodEnter(
        @Advice.Origin final Method method, @Advice.Argument(0) final Request request) {
      request.addAfterServiceListener(SpanClosingListener.LISTENER);

      return TRACER.startSpan(request, method, null);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void methodExit(
        @Advice.Argument(1) final Response response,
        @Advice.Enter final SpanWithScope spanWithScope,
        @Advice.Thrown final Throwable throwable) {

      if (throwable != null) {
        TRACER.endExceptionally(spanWithScope, throwable, response.getStatus());
      }
    }
  }

  public static class SpanClosingListener implements AfterServiceListener {
    public static final SpanClosingListener LISTENER = new SpanClosingListener();
    public static final GrizzlyHttpServerTracer TRACER = new GrizzlyHttpServerTracer();

    @Override
    public void onAfterService(final Request request) {
      final Object spanAttr = request.getAttribute(SPAN_ATTRIBUTE);
      if (spanAttr instanceof Span) {
        request.removeAttribute(SPAN_ATTRIBUTE);
        TRACER.end((Span) spanAttr, request.getResponse().getStatus());
      }
    }
  }
}
