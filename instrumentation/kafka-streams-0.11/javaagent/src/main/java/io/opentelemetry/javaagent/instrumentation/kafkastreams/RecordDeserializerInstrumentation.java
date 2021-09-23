/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams;

import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.isInterface;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPackagePrivate;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.not;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.api.ContextStore;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;

// at some point in time SourceNodeRecordDeserializer was refactored into RecordDeserializer
public class RecordDeserializerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<ClassLoader> classLoaderOptimization() {
    return hasClassesNamed("org.apache.kafka.streams.processor.internals.RecordDeserializer");
  }

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.streams.processor.internals.RecordDeserializer")
        .and(not(isInterface()));
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPackagePrivate())
            .and(named("deserialize"))
            .and(takesArgument(1, named("org.apache.kafka.clients.consumer.ConsumerRecord")))
            .and(returns(named("org.apache.kafka.clients.consumer.ConsumerRecord"))),
        RecordDeserializerInstrumentation.class.getName() + "$DeserializeAdvice");
  }

  @SuppressWarnings("unused")
  public static class DeserializeAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(
        @Advice.Argument(1) ConsumerRecord<?, ?> incoming,
        @Advice.Return(readOnly = false) ConsumerRecord<?, ?> result) {

      // copy the receive CONSUMER span association
      ContextStore<ConsumerRecord, SpanContext> singleRecordReceiveSpan =
          InstrumentationContext.get(ConsumerRecord.class, SpanContext.class);
      singleRecordReceiveSpan.put(result, singleRecordReceiveSpan.get(incoming));
    }
  }
}
