/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.logback.mdc.v1_0;

import static io.opentelemetry.instrumentation.logback.v1_0.MdcPropertyMapHelper.instrument;
import static io.opentelemetry.instrumentation.logback.v1_0.MdcPropertyMapHelper.isInstrumented;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.implementsInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import ch.qos.logback.classic.spi.ILoggingEvent;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.bytecode.assign.Assigner.Typing;
import net.bytebuddy.matcher.ElementMatcher;

public class LoggingEventInstrumentation implements TypeInstrumentation {
  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("ch.qos.logback.classic.spi.ILoggingEvent");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("ch.qos.logback.classic.spi.ILoggingEvent"));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(namedOneOf("getMDCPropertyMap", "getMdc"))
            .and(takesArguments(0)),
        LoggingEventInstrumentation.class.getName() + "$GetMdcAdvice");
  }

  @SuppressWarnings("unused")
  public static class GetMdcAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.This ILoggingEvent event,
        @Advice.Return(typing = Typing.DYNAMIC, readOnly = false) Map<String, String> mdcPropertyMap) {
      Context context = VirtualField.find(ILoggingEvent.class, Context.class).get(event);
      if (context == null) {
        return;
      }

      SpanContext spanContext = Java8BytecodeBridge.spanFromContext(context).getSpanContext();
      if (!spanContext.isValid()) {
        return;
      }

      if (isInstrumented(mdcPropertyMap)) {
        return;
      }

      mdcPropertyMap = instrument(mdcPropertyMap, spanContext);
    }
  }
}
