/*
 * Copyright 2020, OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.opentelemetry.auto.instrumentation.jms;

import static io.opentelemetry.auto.bootstrap.instrumentation.decorator.BaseDecorator.extract;
import static io.opentelemetry.auto.instrumentation.jms.JMSDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.jms.JMSDecorator.TRACER;
import static io.opentelemetry.auto.instrumentation.jms.MessageExtractAdapter.GETTER;
import static io.opentelemetry.auto.tooling.ClassLoaderMatcher.hasClassesNamed;
import static io.opentelemetry.auto.tooling.bytebuddy.matcher.AgentElementMatchers.implementsInterface;
import static io.opentelemetry.trace.Span.Kind.CLIENT;
import static io.opentelemetry.trace.TracingContextUtils.currentContextWith;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.google.auto.service.AutoService;
import io.opentelemetry.auto.tooling.Instrumenter;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import io.opentelemetry.trace.SpanContext;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

@AutoService(Instrumenter.class)
public final class JMSMessageConsumerInstrumentation extends Instrumenter.Default {

  public JMSMessageConsumerInstrumentation() {
    super("jms", "jms-1", "jms-2");
  }

  @Override
  public ElementMatcher<ClassLoader> classLoaderMatcher() {
    // Optimization for expensive typeMatcher.
    return hasClassesNamed("javax.jms.MessageConsumer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return implementsInterface(named("javax.jms.MessageConsumer"));
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
      packageName + ".JMSDecorator",
      packageName + ".MessageExtractAdapter",
      packageName + ".MessageInjectAdapter"
    };
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    final Map<ElementMatcher<? super MethodDescription>, String> transformers = new HashMap<>();
    transformers.put(
        named("receive").and(takesArguments(0).or(takesArguments(1))).and(isPublic()),
        JMSMessageConsumerInstrumentation.class.getName() + "$ConsumerAdvice");
    transformers.put(
        named("receiveNoWait").and(takesArguments(0)).and(isPublic()),
        JMSMessageConsumerInstrumentation.class.getName() + "$ConsumerAdvice");
    return transformers;
  }

  public static class ConsumerAdvice {

    @Advice.OnMethodEnter
    public static long onEnter() {
      return System.currentTimeMillis();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.This final MessageConsumer consumer,
        @Advice.Enter final long startTime,
        @Advice.Origin final Method method,
        @Advice.Return final Message message,
        @Advice.Thrown final Throwable throwable) {
      final String spanName;
      if (message == null) {
        spanName = DECORATE.spanNameForReceive(method);
      } else {
        spanName = DECORATE.spanNameForReceive(message);
      }
      final Span.Builder spanBuilder =
          TRACER
              .spanBuilder(spanName)
              .setSpanKind(CLIENT)
              .setStartTimestamp(TimeUnit.MILLISECONDS.toNanos(startTime));
      if (message != null) {
        final SpanContext spanContext = extract(message, GETTER);
        if (spanContext.isValid()) {
          spanBuilder.addLink(spanContext);
        }
      }
      final Span span = spanBuilder.startSpan();
      span.setAttribute("span.origin.type", consumer.getClass().getName());

      try (final Scope scope = currentContextWith(span)) {
        DECORATE.afterStart(span);
        DECORATE.onError(span, throwable);
        DECORATE.beforeFinish(span);
        span.end();
      }
    }
  }
}
