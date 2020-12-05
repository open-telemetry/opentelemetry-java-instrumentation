/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticsearchTransportClientTracer.tracer;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.InstrumentationModule;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.List;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;

/**
 * Most of this class is identical to version 5's instrumentation, but they changed an interface to
 * an abstract class, so the bytecode isn't directly compatible.
 */
@AutoService(InstrumentationModule.class)
public class Elasticsearch6TransportClientInstrumentationModule extends InstrumentationModule {
  public Elasticsearch6TransportClientInstrumentationModule() {
    super("elasticsearch-transport", "elasticsearch-transport-6.0", "elasticsearch");
  }

  @Override
  public List<TypeInstrumentation> typeInstrumentations() {
    return singletonList(new AbstractClientInstrumentation());
  }

  public static class AbstractClientInstrumentation implements TypeInstrumentation {
    @Override
    public ElementMatcher<TypeDescription> typeMatcher() {
      // If we want to be more generic, we could instrument the interface instead:
      // .and(safeHasSuperType(named("org.elasticsearch.client.ElasticsearchClient"))))
      return named("org.elasticsearch.client.support.AbstractClient");
    }

    @Override
    public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
      return singletonMap(
          isMethod()
              .and(named("execute"))
              .and(takesArgument(0, named("org.elasticsearch.action.Action")))
              .and(takesArgument(1, named("org.elasticsearch.action.ActionRequest")))
              .and(takesArgument(2, named("org.elasticsearch.action.ActionListener"))),
          Elasticsearch6TransportClientInstrumentationModule.class.getName()
              + "$Elasticsearch6TransportClientAdvice");
    }
  }

  public static class Elasticsearch6TransportClientAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) Action action,
        @Advice.Argument(1) ActionRequest actionRequest,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope,
        @Advice.Argument(value = 2, readOnly = false)
            ActionListener<ActionResponse> actionListener) {

      context = tracer().startSpan(currentContext(), null, action);
      scope = context.makeCurrent();
      tracer().onRequest(context, action.getClass(), actionRequest.getClass());
      actionListener = new TransportActionListener<>(actionRequest, actionListener, context);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();

      if (throwable != null) {
        tracer().endExceptionally(context, throwable);
      }
    }
  }
}
