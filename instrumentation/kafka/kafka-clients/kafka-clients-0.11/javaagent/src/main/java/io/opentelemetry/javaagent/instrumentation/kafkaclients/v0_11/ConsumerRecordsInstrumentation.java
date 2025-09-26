/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing.wrappingEnabledSupplier;
import static io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.KafkaSingletons.consumerProcessInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isMethod;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.TracingIterable;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.TracingIterator;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.TracingList;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.TracingListIterator;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.Advice.AssignReturned;
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
    transformer.applyAdviceToMethod(
        isMethod()
            .and(isPublic())
            .and(named("listIterator"))
            .and(takesArguments(0))
            .and(returns(ListIterator.class)),
        ConsumerRecordsInstrumentation.class.getName() + "$ListIteratorAdvice");
  }

  @SuppressWarnings("unused")
  public static class IterableAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> Iterable<ConsumerRecord<K, V>> wrap(
        @Advice.This ConsumerRecords<?, ?> records,
        @Advice.Return Iterable<ConsumerRecord<K, V>> iterable) {

      // it's important not to suppress consumer span creation here because this instrumentation can
      // leak the context and so there may be a leaked consumer span in the context, in which
      // case it's important to overwrite the leaked span instead of suppressing the correct span
      // (https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1947)
      KafkaConsumerContext consumerContext = KafkaConsumerContextUtil.get(records);
      return TracingIterable.wrap(
          iterable, consumerProcessInstrumenter(), wrappingEnabledSupplier(), consumerContext);
    }
  }

  @SuppressWarnings("unused")
  public static class ListAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> List<ConsumerRecord<K, V>> wrap(
        @Advice.This ConsumerRecords<?, ?> records,
        @Advice.Return List<ConsumerRecord<K, V>> list) {

      // it's important not to suppress consumer span creation here because this instrumentation can
      // leak the context and so there may be a leaked consumer span in the context, in which
      // case it's important to overwrite the leaked span instead of suppressing the correct span
      // (https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1947)
      KafkaConsumerContext consumerContext = KafkaConsumerContextUtil.get(records);
      return TracingList.wrap(
          list, consumerProcessInstrumenter(), wrappingEnabledSupplier(), consumerContext);
    }
  }

  @SuppressWarnings("unused")
  public static class IteratorAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> Iterator<ConsumerRecord<K, V>> wrap(
        @Advice.This ConsumerRecords<?, ?> records,
        @Advice.Return Iterator<ConsumerRecord<K, V>> iterator) {

      // it's important not to suppress consumer span creation here because this instrumentation can
      // leak the context and so there may be a leaked consumer span in the context, in which
      // case it's important to overwrite the leaked span instead of suppressing the correct span
      // (https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1947)
      KafkaConsumerContext consumerContext = KafkaConsumerContextUtil.get(records);
      return TracingIterator.wrap(
          iterator, consumerProcessInstrumenter(), wrappingEnabledSupplier(), consumerContext);
    }
  }

  @SuppressWarnings("unused")
  public static class ListIteratorAdvice {

    @AssignReturned.ToReturned
    @Advice.OnMethodExit(suppress = Throwable.class)
    public static <K, V> ListIterator<ConsumerRecord<K, V>> wrap(
        @Advice.This ConsumerRecords<?, ?> records,
        @Advice.Return ListIterator<ConsumerRecord<K, V>> listIterator) {

      // it's important not to suppress consumer span creation here because this instrumentation can
      // leak the context and so there may be a leaked consumer span in the context, in which
      // case it's important to overwrite the leaked span instead of suppressing the correct span
      // (https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/1947)
      KafkaConsumerContext consumerContext = KafkaConsumerContextUtil.get(records);
      return TracingListIterator.wrap(
          listIterator, consumerProcessInstrumenter(), wrappingEnabledSupplier(), consumerContext);
    }
  }
}
