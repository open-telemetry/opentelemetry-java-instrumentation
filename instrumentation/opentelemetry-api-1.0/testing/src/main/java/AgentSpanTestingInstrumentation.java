/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

import static net.bytebuddy.matcher.ElementMatchers.named;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class AgentSpanTestingInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("AgentSpanTesting");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("runWithServerSpan"), this.getClass().getName() + "$RunWithServerSpanAdvice");
    transformer.applyAdviceToMethod(
        named("runWithConsumerSpan"), this.getClass().getName() + "$RunWithConsumerSpanAdvice");
    transformer.applyAdviceToMethod(
        named("runWithClientSpan"), this.getClass().getName() + "$RunWithClientSpanAdvice");
    transformer.applyAdviceToMethod(
        named("runWithAllSpanKeys"), this.getClass().getName() + "$RunWithAllSpanKeysAdvice");
  }

  @SuppressWarnings("unused")
  public static class RunWithServerSpanAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) String spanName,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      context = AgentSpanTestingTracer.tracer().startServerSpan(spanName);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      if (throwable != null) {
        AgentSpanTestingTracer.tracer().endExceptionally(context, throwable);
      } else {
        AgentSpanTestingTracer.tracer().end(context);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RunWithConsumerSpanAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) String spanName,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      context = AgentSpanTestingTracer.tracer().startConsumerSpan(spanName);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      if (throwable != null) {
        AgentSpanTestingTracer.tracer().endExceptionally(context, throwable);
      } else {
        AgentSpanTestingTracer.tracer().end(context);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RunWithClientSpanAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) String spanName,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      context = AgentSpanTestingTracer.tracer().startClientSpan(spanName);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      if (throwable != null) {
        AgentSpanTestingTracer.tracer().endExceptionally(context, throwable);
      } else {
        AgentSpanTestingTracer.tracer().end(context);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class RunWithAllSpanKeysAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.Argument(0) String spanName,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      context = AgentSpanTestingTracer.tracer().startSpanWithAllKeys(spanName);
      scope = context.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void onExit(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelContext") Context context,
        @Advice.Local("otelScope") Scope scope) {
      scope.close();
      if (throwable != null) {
        AgentSpanTestingTracer.tracer().endExceptionally(context, throwable);
      } else {
        AgentSpanTestingTracer.tracer().end(context);
      }
    }
  }
}
