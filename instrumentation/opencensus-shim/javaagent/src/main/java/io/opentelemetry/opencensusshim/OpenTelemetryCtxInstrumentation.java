/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.opencensusshim;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import application.io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Doctors the {@link OpenTelemetryCtx} constructor to provide a delegating wrapper for the provided
 * {@link Context} which very specifically singles out the {@link OpenTelemetrySpanImpl} shortcoming
 * upon calling the {@link Context#with} methods.
 *
 * <p>The "with" methods then extract the internal otel span and pass it along for processing as
 * normal as, sadly, the Java Agent instrumentations all <i>require</i> the agent-generated
 * <i>instance</i>: interface conformance is simply not enough when the java agent is in the mix as
 * it uses data stored in specialized instances of {@link
 * application.io.opentelemetry.api.trace.Span} to perform its duties.
 */
public class OpenTelemetryCtxInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("io.opentelemetry.opencensusshim.OpenTelemetryCtx");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor()
            .and(takesArgument(0, named("application.io.opentelemetry.context.Context"))),
        OpenTelemetryCtxInstrumentation.class.getName() + "$HandleConstruction");
  }

  @SuppressWarnings({"unused", "OtelPrivateConstructorForUtilityClass"})
  public static class HandleConstruction {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void constructor(@Advice.Argument(value = 0, readOnly = false) Context context) {
      context = new ContextExtractor(context);
    }
  }
}
