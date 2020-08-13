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

package io.opentelemetry.auto.instrumentation.elasticsearch.transport.v2_0;

import static io.opentelemetry.auto.instrumentation.elasticsearch.transport.ElasticsearchTransportClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.elasticsearch.transport.ElasticsearchTransportClientDecorator.TRACER;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.instrumentation.auto.api.SpanWithScope;
import io.opentelemetry.trace.Span;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;

@AutoService(Instrumenter.class)
public class Elasticsearch2TransportClientInstrumentation extends Instrumenter.Default {

  public Elasticsearch2TransportClientInstrumentation() {
    super("elasticsearch", "elasticsearch-transport", "elasticsearch-transport-2");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // If we want to be more generic, we could instrument the interface instead:
    // .and(safeHasSuperType(named("org.elasticsearch.client.ElasticsearchClient"))))
    return named("org.elasticsearch.client.support.AbstractClient");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      "com.google.common.base.Preconditions",
      "com.google.common.base.Joiner",
      "com.google.common.base.Joiner$1",
      "com.google.common.base.Joiner$2",
      "com.google.common.base.Joiner$MapJoiner",
      "io.opentelemetry.auto.instrumentation.elasticsearch.transport.ElasticsearchTransportClientDecorator",
      packageName + ".TransportActionListener",
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(named("execute"))
            .and(takesArgument(0, named("org.elasticsearch.action.Action")))
            .and(takesArgument(1, named("org.elasticsearch.action.ActionRequest")))
            .and(takesArgument(2, named("org.elasticsearch.action.ActionListener"))),
        Elasticsearch2TransportClientInstrumentation.class.getName()
            + "$ElasticsearchTransportClientAdvice");
  }

  public static class ElasticsearchTransportClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static SpanWithScope onEnter(
        @Advice.Argument(0) final Action action,
        @Advice.Argument(1) final ActionRequest actionRequest,
        @Advice.Argument(value = 2, readOnly = false)
            ActionListener<ActionResponse> actionListener) {

      Span span =
          TRACER.spanBuilder(action.getClass().getSimpleName()).setSpanKind(CLIENT).startSpan();
      DECORATE.afterStart(span);
      DECORATE.onRequest(span, action.getClass(), actionRequest.getClass());

      actionListener = new TransportActionListener<>(actionRequest, actionListener, span);

      return new SpanWithScope(span, currentContextWith(span));
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Enter final SpanWithScope spanWithScope, @Advice.Thrown final Throwable throwable) {
      if (throwable != null) {
        Span span = spanWithScope.getSpan();
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
      }
      spanWithScope.closeScope();
      // span finished by TransportActionListener
    }
  }
}
