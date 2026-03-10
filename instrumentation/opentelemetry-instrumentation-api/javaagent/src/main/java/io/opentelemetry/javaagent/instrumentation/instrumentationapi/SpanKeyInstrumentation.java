/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationapi;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

final class SpanKeyInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("application.io.opentelemetry.instrumentation.api.internal.SpanKey");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("storeInContext")
            .and(takesArgument(0, named("application.io.opentelemetry.context.Context")))
            .and(takesArgument(1, named("application.io.opentelemetry.api.trace.Span"))),
        this.getClass().getName() + "$StoreInContextAdvice");
    transformer.applyAdviceToMethod(
        named("fromContextOrNull")
            .and(takesArgument(0, named("application.io.opentelemetry.context.Context"))),
        this.getClass().getName() + "$FromContextOrNullAdvice");
  }

  @SuppressWarnings("unused")
  public static class StoreInContextAdvice {

    @Nullable
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static application.io.opentelemetry.context.Context onEnter(
        @Advice.This
            application.io.opentelemetry.instrumentation.api.internal.SpanKey applicationSpanKey,
        @Advice.Argument(0) application.io.opentelemetry.context.Context applicationContext,
        @Advice.Argument(1) application.io.opentelemetry.api.trace.Span applicationSpan) {

      SpanKey agentSpanKey = SpanKeyBridging.toAgentOrNull(applicationSpanKey);
      if (agentSpanKey == null) {
        return null;
      }

      Context agentContext = AgentContextStorage.getAgentContext(applicationContext);

      Span agentSpan = Bridging.toAgentOrNull(applicationSpan);
      if (agentSpan == null) {
        // if application span can not be bridged to agent span, this could happen when it is not
        // created through bridged GlobalOpenTelemetry, we'll let the original method run and
        // store the span in context without bridging
        return null;
      }

      Context newAgentContext = agentSpanKey.storeInContext(agentContext, agentSpan);

      return AgentContextStorage.toApplicationContext(newAgentContext);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static application.io.opentelemetry.context.Context onExit(
        @Advice.Return application.io.opentelemetry.context.Context originalResult,
        @Advice.Enter @Nullable
            application.io.opentelemetry.context.Context newApplicationContext) {
      return newApplicationContext != null ? newApplicationContext : originalResult;
    }
  }

  @SuppressWarnings("unused")
  public static class FromContextOrNullAdvice {

    @Nullable
    @Advice.OnMethodEnter(skipOn = Advice.OnNonDefaultValue.class)
    public static application.io.opentelemetry.api.trace.Span onEnter(
        @Advice.This
            application.io.opentelemetry.instrumentation.api.internal.SpanKey applicationSpanKey,
        @Advice.Argument(0) application.io.opentelemetry.context.Context applicationContext) {

      SpanKey agentSpanKey = SpanKeyBridging.toAgentOrNull(applicationSpanKey);
      if (agentSpanKey == null) {
        return null;
      }

      Context agentContext = AgentContextStorage.getAgentContext(applicationContext);

      Span agentSpan = agentSpanKey.fromContextOrNull(agentContext);
      if (agentSpan == null) {
        // Bridged agent span was not found. Run the original method, there could be an unbridged
        // span stored in the application context.
        return null;
      }

      return Bridging.toApplication(agentSpan);
    }

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static application.io.opentelemetry.api.trace.Span onExit(
        @Advice.Return application.io.opentelemetry.api.trace.Span originalResult,
        @Advice.Enter @Nullable application.io.opentelemetry.api.trace.Span applicationSpan) {
      return applicationSpan != null ? applicationSpan : originalResult;
    }
  }
}
