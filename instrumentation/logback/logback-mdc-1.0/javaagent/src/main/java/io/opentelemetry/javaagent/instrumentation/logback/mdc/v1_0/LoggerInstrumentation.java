/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0;

import static io.opentelemetry.javaagent.bootstrap.internal.LogbackMdcState.currentExportRead;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.takesArguments;
import static io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0.LogbackSingletons.CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class LoggerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("ch.qos.logback.classic.Logger");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isPublic()
            .and(named("callAppenders"))
            .and(takesArguments(named("ch.qos.logback.classic.spi.ILoggingEvent"))),
        getClass().getName() + "$CallAppendersAdvice");
  }

  @SuppressWarnings("unused")
  public static class CallAppendersAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    @Nullable
    public static Boolean onEnter(@Advice.Argument(0) ILoggingEvent event) {
      CONTEXT.set(event, Context.current());
      return currentExportRead().set(null);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Boolean previous) {
      currentExportRead().restore(previous);
    }
  }
}
