/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import static io.opentelemetry.javaagent.instrumentation.kafkaclients.KafkaProducerTracer.tracer;
import static io.opentelemetry.javaagent.instrumentation.kafkaclients.TextMapInjectAdapter.SETTER;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge;
import io.opentelemetry.javaagent.tooling.TypeInstrumentation;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.ApiVersions;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;

public class KafkaProducerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.clients.producer.KafkaProducer");
  }

  @Override
  public Map<? extends ElementMatcher<? super MethodDescription>, String> transformers() {
    return singletonMap(
        isMethod()
            .and(isPublic())
            .and(named("send"))
            .and(takesArgument(0, named("org.apache.kafka.clients.producer.ProducerRecord")))
            .and(takesArgument(1, named("org.apache.kafka.clients.producer.Callback"))),
        KafkaProducerInstrumentation.class.getName() + "$ProducerAdvice");
  }

  public static class ProducerAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(
        @Advice.FieldValue("apiVersions") ApiVersions apiVersions,
        @Advice.Argument(value = 0, readOnly = false) ProducerRecord<?, ?> record,
        @Advice.Argument(value = 1, readOnly = false) Callback callback,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {

      Context parent = Java8BytecodeBridge.currentContext();

      span = tracer().startProducerSpan(record);
      Context newContext = parent.with(span);

      callback = new ProducerCallback(callback, parent, span);

      if (tracer().shouldPropagate(apiVersions)) {
        try {
          Java8BytecodeBridge.getGlobalPropagators()
              .getTextMapPropagator()
              .inject(newContext, record.headers(), SETTER);
        } catch (IllegalStateException e) {
          // headers must be read-only from reused record. try again with new one.
          record =
              new ProducerRecord<>(
                  record.topic(),
                  record.partition(),
                  record.timestamp(),
                  record.key(),
                  record.value(),
                  record.headers());

          Java8BytecodeBridge.getGlobalPropagators()
              .getTextMapPropagator()
              .inject(newContext, record.headers(), SETTER);
        }
      }

      scope = newContext.makeCurrent();
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(
        @Advice.Thrown Throwable throwable,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {

      scope.close();

      if (throwable != null) {
        tracer().endExceptionally(span, throwable);
      }
      // span finished by ProducerCallback
    }
  }
}
