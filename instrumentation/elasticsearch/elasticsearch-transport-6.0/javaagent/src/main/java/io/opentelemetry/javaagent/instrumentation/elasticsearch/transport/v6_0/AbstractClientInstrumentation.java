/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0.Elasticsearch6TransportSingletons.instrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
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
            .and(
                takesArgument(
                    0,
                    namedOneOf(
                        "org.elasticsearch.action.Action", "org.elasticsearch.action.ActionType")))
            .and(takesArgument(1, named("org.elasticsearch.action.ActionRequest")))
            .and(takesArgument(2, named("org.elasticsearch.action.ActionListener"))),
        this.getClass().getName() + "$ExecuteAdvice");
  }

  @SuppressWarnings("unused")
  public static class ExecuteAdvice {

    public static class AdviceScope {
      private final ElasticTransportRequest request;
      private final Context parentContext;
      private final Context context;
      private final Scope scope;

      private AdviceScope(
          ElasticTransportRequest request, Context parentContext, Context context, Scope scope) {
        this.request = request;
        this.parentContext = parentContext;
        this.context = context;
        this.scope = scope;
      }

      public static AdviceScope start(ElasticTransportRequest request) {
        Context parentContext = currentContext();
        if (!instrumenter().shouldStart(parentContext, request)) {
          return null;
        }
        Context context = instrumenter().start(parentContext, request);
        return new AdviceScope(request, parentContext, context, context.makeCurrent());
      }

      public ActionListener<ActionResponse> wrapListener(
          ActionListener<ActionResponse> actionListener) {
        return new TransportActionListener<>(
            instrumenter(), request, actionListener, context, parentContext);
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
        @Advice.Argument(0) Object action,
        @Advice.Argument(1) ActionRequest actionRequest,
        @Advice.Argument(2) ActionListener<ActionResponse> originalActionListener) {
      ActionListener<ActionResponse> actionListener = originalActionListener;

      ElasticTransportRequest request = ElasticTransportRequest.create(action, actionRequest);
      AdviceScope adviceScope = AdviceScope.start(request);
      if (adviceScope == null) {
        return new Object[] {null, actionListener};
      }

      actionListener = adviceScope.wrapListener(actionListener);
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
