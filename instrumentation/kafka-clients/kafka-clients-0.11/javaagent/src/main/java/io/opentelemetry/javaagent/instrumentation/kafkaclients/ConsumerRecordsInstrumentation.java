/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.instrumentation.api.instrumenter.SpanKey;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Iterator;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public class ConsumerRecordsInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.clients.consumer.ConsumerRecords");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("records"))
            .and(takesArgument(0, String.class))
            .and(returns(Iterable.class)),
        ConsumerRecordsInstrumentation.class.getName() + "$IterableAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("records"))
            .and(takesArgument(0, named("org.apache.kafka.common.TopicPartition")))
            .and(returns(List.class)),
        ConsumerRecordsInstrumentation.class.getName() + "$ListAdvice");
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("iterator"))
            .and(takesArguments(0))
            .and(returns(Iterator.class)),
        ConsumerRecordsInstrumentation.class.getName() + "$IteratorAdvice");
  }

  @SuppressWarnings("unused")
  public static class IterableAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> void wrap(
        @Advice.This ConsumerRecords<?, ?> records,
        @Advice.Return(readOnly = false) Iterable<ConsumerRecord<K, V>> iterable) {
      if (iterable != null
          && SpanKey.CONSUMER_PROCESS.fromContextOrNull(currentContext()) == null) {
        Context receiveContext =
            VirtualField.find(ConsumerRecords.class, Context.class).get(records);
        iterable = TracingIterable.wrap(iterable, receiveContext);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ListAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> void wrap(
        @Advice.This ConsumerRecords<?, ?> records,
        @Advice.Return(readOnly = false) List<ConsumerRecord<K, V>> list) {
      if (list != null) {
        Context receiveContext =
            VirtualField.find(ConsumerRecords.class, Context.class).get(records);
        list = TracingList.wrap(list, receiveContext);
      }
    }
  }

  @SuppressWarnings("unused")
  public static class IteratorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> void wrap(
        @Advice.This ConsumerRecords<?, ?> records,
        @Advice.Return(readOnly = false) Iterator<ConsumerRecord<K, V>> iterator) {
      if (iterator != null
          && SpanKey.CONSUMER_PROCESS.fromContextOrNull(currentContext()) == null) {
        Context receiveContext =
            VirtualField.find(ConsumerRecords.class, Context.class).get(records);
        iterator = TracingIterator.wrap(iterator, receiveContext);
      }
    }
  }
}
