/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.rabbitmq;

import static io.opentelemetry.javaagent.instrumentation.rabbitmq.RabbitCommandInstrumentation.SpanHolder.CURRENT_RABBIT_CONTEXT;
import static io.opentelemetry.javaagent.instrumentation.rabbitmq.RabbitTracer.tracer;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.javaagent.tooling.bytebuddy.matcher.ClassLoaderMatcher.hasClassesNamed;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;

import com.rabbitmq.client.Command;
import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

public class RabbitCommandInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("com.rabbitmq.client.Command");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("com.rabbitmq.client.Command"));
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isConstructor(),
        RabbitCommandInstrumentation.class.getName() + "$CommandConstructorAdvice");
  }

  public static class SpanHolder {
    public static final ThreadLocal<Context> CURRENT_RABBIT_CONTEXT = new ThreadLocal<>();
  }

  public static class CommandConstructorAdvice {
    @Advice.OnMethodExit
    public static void setSpanNameAddHeaders(@Advice.This Command command) {

      Context context = CURRENT_RABBIT_CONTEXT.get();
      if (context != null && command.getMethod() != null) {
        tracer().onCommand(Java8BytecodeBridge.spanFromContext(context), command);
      }
    }
  }
}
