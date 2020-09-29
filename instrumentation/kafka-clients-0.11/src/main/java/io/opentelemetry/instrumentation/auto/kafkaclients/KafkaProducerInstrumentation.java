/*
 * Copyright The OpenTelemetry Authors
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

package io.opentelemetry.instrumentation.auto.kafkaclients;

import static io.opentelemetry.context.ContextUtils.withScopedContext;
import static io.opentelemetry.instrumentation.auto.kafkaclients.KafkaProducerTracer.TRACER;
import static io.opentelemetry.instrumentation.auto.kafkaclients.TextMapInjectAdapter.SETTER;
import static io.opentelemetry.trace.TracingContextUtils.withSpan;
import static java.util.Collections.singletonMap;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.auto.service.AutoService;
import io.grpc.Context;
import io.opentelemetry.OpenTelemetry;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.tooling.Instrumenter;
import io.opentelemetry.trace.Span;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.method.MethodDescription;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.ApiVersions;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

@AutoService(Instrumenter.class)
public final class KafkaProducerInstrumentation extends Instrumenter.Default {

  public KafkaProducerInstrumentation() {
    super("kafka");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.clients.producer.KafkaProducer");
  }

  @Override
  public String[] helperClassNames() {
    return new String[] {
        packageName + ".KafkaClientConfiguration",
      packageName + ".KafkaProducerTracer",
        packageName + ".TextMapInjectAdapter",
        KafkaProducerInstrumentation.class.getName() + "$ProducerCallback"
    };
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
        @Advice.Argument(value = 0, readOnly = false) ProducerRecord record,
        @Advice.Argument(value = 1, readOnly = false) Callback callback,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {

      Context parent = Context.current();

      span = TRACER.startProducerSpan(record);
      Context newContext = withSpan(span, parent);

      callback = new ProducerCallback(callback, parent, span);

      if (TRACER.shouldPropagate(apiVersions)) {
        try {
          OpenTelemetry.getPropagators()
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

          OpenTelemetry.getPropagators()
              .getTextMapPropagator()
              .inject(newContext, record.headers(), SETTER);
        }
      }

      scope = withScopedContext(newContext);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
    public static void stopSpan(@Advice.Thrown Throwable throwable,
        @Advice.Local("otelSpan") Span span,
        @Advice.Local("otelScope") Scope scope) {

      scope.close();

      if (throwable != null) {
        TRACER.endExceptionally(span, throwable);
      }
      // span finished by ProducerCallback
    }
  }

  public static class ProducerCallback implements Callback {
    private final Callback callback;
    private final Context parent;
    private final Span span;

    public ProducerCallback(Callback callback, Context parent, Span span) {
      this.callback = callback;
      this.parent = parent;
      this.span = span;
    }

    @Override
    public void onCompletion(RecordMetadata metadata, Exception exception) {
      if (exception != null) {
        TRACER.endExceptionally(span, exception);
      } else {
        TRACER.end(span);
      }

      if (callback != null) {
        if (parent != null) {
          try (Scope ignored = withScopedContext(parent)) {
            callback.onCompletion(metadata, exception);
          }
        } else {
          callback.onCompletion(metadata, exception);
        }
      }
    }
  }
}
