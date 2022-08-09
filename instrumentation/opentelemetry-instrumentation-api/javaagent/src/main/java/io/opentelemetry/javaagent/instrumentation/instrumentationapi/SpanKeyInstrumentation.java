/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.instrumentationapi;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import application.io.opentelemetry.api.trace.Span;
import application.io.opentelemetry.context.Context;
import application.io.opentelemetry.instrumentation.api.internal.SpanKey;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.context.AgentContextStorage;
import io.opentelemetry.javaagent.instrumentation.opentelemetryapi.trace.Bridging;
import net.bytebuddy.asm.Advice;
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
    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This SpanKey applicationSpanKey,
        @Advice.Argument(0) Context applicationContext,
        @Advice.Argument(1) Span applicationSpan,
        @Advice.Return(readOnly = false) Context newApplicationContext) {

      io.opentelemetry.instrumentation.api.internal.SpanKey agentSpanKey =
          SpanKeyBridging.toAgentOrNull(applicationSpanKey);
      if (agentSpanKey == null) {
        return;
      }

      io.opentelemetry.context.Context agentContext =
          AgentContextStorage.getAgentContext(applicationContext);

      io.opentelemetry.api.trace.Span agentSpan = Bridging.toAgentOrNull(applicationSpan);
      if (agentSpan == null) {
        return;
      }

      io.opentelemetry.context.Context newAgentContext =
          agentSpanKey.storeInContext(agentContext, agentSpan);

      newApplicationContext = AgentContextStorage.toApplicationContext(newAgentContext);
    }
  }

  @SuppressWarnings("unused")
  public static class FromContextOrNullAdvice {
    @Advice.OnMethodEnter(skipOn = Advice.OnDefaultValue.class)
    public static Object onEnter() {
      return null;
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This SpanKey applicationSpanKey,
        @Advice.Argument(0) Context applicationContext,
        @Advice.Return(readOnly = false) Span applicationSpan) {

      io.opentelemetry.instrumentation.api.internal.SpanKey agentSpanKey =
          SpanKeyBridging.toAgentOrNull(applicationSpanKey);
      if (agentSpanKey == null) {
        return;
      }

      io.opentelemetry.context.Context agentContext =
          AgentContextStorage.getAgentContext(applicationContext);

      io.opentelemetry.api.trace.Span agentSpan = agentSpanKey.fromContextOrNull(agentContext);

      applicationSpan = agentSpan == null ? null : Bridging.toApplication(agentSpan);
    }
  }
}
