/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0;

import static io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0.LogbackSingletons.CONTEXT;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
import net.bytebuddy.asm.Advice.AssignReturned.ToArguments.ToArgument;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class LoggerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("ch.qos.logback.classic.Logger");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("callAppenders"))
            .and(takesArguments(1))
            .and(takesArgument(0, named("ch.qos.logback.classic.spi.ILoggingEvent"))),
        LoggerInstrumentation.class.getName() + "$CallAppendersAdvice");
  }

  @SuppressWarnings("unused")
  public static class CallAppendersAdvice {

    @AssignReturned.ToArguments(@ToArgument(0))
    @Advice.OnMethodEnter
    public static ILoggingEvent onEnter(@Advice.Argument(0) ILoggingEvent event) {
      CONTEXT.set(event, Java8BytecodeBridge.currentContext());
      return event;
    }
  }
}
