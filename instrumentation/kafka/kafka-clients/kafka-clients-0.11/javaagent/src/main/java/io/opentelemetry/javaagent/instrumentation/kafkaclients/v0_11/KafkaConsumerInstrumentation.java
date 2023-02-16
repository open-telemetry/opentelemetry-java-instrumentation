/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11;

import static io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.KafkaSingletons.consumerReceiveInstrumenter;
import static io.opentelemetry.javaagent.instrumentation.kafkaclients.v0_11.KafkaSingletons.enhanceConfig;
import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.instrumentation.kafka.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafka.internal.Timer;
import io.opentelemetry.javaagent.bootstrap.kafka.KafkaClientsConsumerProcessTracing;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public class KafkaConsumerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.clients.consumer.KafkaConsumer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, Map.class)),
        this.getClass().getName() + "$ConstructorMapAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, Properties.class)),
        this.getClass().getName() + "$ConstructorPropertiesAdvice");
    transformer.applyAdviceToMethod(
        named("poll")
            .and(isPublic())
            .and(takesArguments(1))
            .and(takesArgument(0, long.class).or(takesArgument(0, Duration.class)))
            .and(returns(named("org.apache.kafka.clients.consumer.ConsumerRecords"))),
        this.getClass().getName() + "$PollAdvice");
  }

  @SuppressWarnings("unused")
  public static class ConstructorMapAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) Map<String, Object> config) {
      enhanceConfig(config);
    }
  }

  @SuppressWarnings("unused")
  public static class ConstructorPropertiesAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Argument(0) Properties config) {
      enhanceConfig(config);
    }
  }

  @SuppressWarnings("unused")
  public static class PollAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Timer onEnter() {
      return Timer.start();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        // @Advice.FieldValue("consumerGroup") String consumerGroup,
        @Advice.FieldValue("clientId") String clientId,
        @Advice.Enter Timer timer,
        @Advice.Return ConsumerRecords<?, ?> records,
        @Advice.Thrown Throwable error) {

      String consumerGroup = null;
      // don't create spans when no records were received
      if (records == null || records.isEmpty()) {
        return;
      }

      Context parentContext = currentContext();
      if (consumerReceiveInstrumenter().shouldStart(parentContext, records)) {
        // disable process tracing and store the receive span for each individual record too
        boolean previousValue = KafkaClientsConsumerProcessTracing.setEnabled(false);
        try {
          Context context =
              InstrumenterUtil.startAndEnd(
                  consumerReceiveInstrumenter(),
                  parentContext,
                  records,
                  null,
                  error,
                  timer.startTime(),
                  timer.now());

          KafkaConsumerContext consumerContext =
              new KafkaConsumerContext(context, consumerGroup, clientId);
          // we're storing the context of the receive span so that process spans can use it as
          // parent context even though the span has ended
          // this is the suggested behavior according to the spec batch receive scenario:
          // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/messaging.md#batch-receiving
          VirtualField<ConsumerRecords<?, ?>, KafkaConsumerContext> consumerRecordsContext =
              VirtualField.find(ConsumerRecords.class, KafkaConsumerContext.class);
          consumerRecordsContext.set(records, consumerContext);

          VirtualField<ConsumerRecord<?, ?>, KafkaConsumerContext> consumerRecordContext =
              VirtualField.find(ConsumerRecord.class, KafkaConsumerContext.class);
          for (ConsumerRecord<?, ?> record : records) {
            consumerRecordContext.set(record, consumerContext);
          }
        } finally {
          KafkaClientsConsumerProcessTracing.setEnabled(previousValue);
        }
      }
    }
  }
}
