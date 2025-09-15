/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v5_3.Elasticsearch53TransportSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportRequest;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.TransportActionListener;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.elasticsearch.action.Action;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.ActionRequest;
import org.elasticsearch.action.ActionResponse;

public class AbstractClientInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    // If we want to be more generic, we could instrument the interface instead:
    // .and(safeHasSuperType(named("org.elasticsearch.client.ElasticsearchClient"))))
    return named("org.elasticsearch.client.support.AbstractClient");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(named("execute"))
            .and(takesArgument(0, named("org.elasticsearch.action.Action")))
            .and(takesArgument(1, named("org.elasticsearch.action.ActionRequest")))
            .and(takesArgument(2, named("org.elasticsearch.action.ActionListener"))),
        this.getClass().getName() + "$ExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

    public static class AdviceScope {
      private final ElasticTransportRequest request;
      private final Context context;
      private final Scope scope;

      public AdviceScope(ElasticTransportRequest request, Context context, Scope scope) {
        this.request = request;
        this.context = context;
        this.scope = scope;
      }

      public void end(@Nullable Throwable throwable) {
        scope.close();
        if (throwable != null) {
          instrumenter().end(context, request, null, throwable);
        }
      }
    }

    @AssignReturned.ToArguments(@ToArgument(value = 2, index = 1))
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Object[] onEnter(
        @Advice.Argument(0) Action<?, ?, ?> action,
        @Advice.Argument(1) ActionRequest actionRequest,
        @Advice.Argument(2) ActionListener<ActionResponse> originalActionListener) {
      ActionListener<ActionResponse> actionListener = originalActionListener;

      ElasticTransportRequest request = ElasticTransportRequest.create(action, actionRequest);
      Context parentContext = currentContext();
      if (!instrumenter().shouldStart(parentContext, request)) {
        return new Object[] {null, actionListener};
      }

      Context context = instrumenter().start(parentContext, request);
      AdviceScope adviceScope = new AdviceScope(request, context, context.makeCurrent());
      actionListener =
          new TransportActionListener<>(
              instrumenter(), request, actionListener, context, parentContext);
      return new Object[] {adviceScope, actionListener};
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown @Nullable Throwable throwable, @Advice.Enter Object[] enterResult) {
      AdviceScope adviceScope = (AdviceScope) enterResult[0];
      if (adviceScope != null) {
        adviceScope.end(throwable);
      }
    }
  }
}
