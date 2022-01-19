/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaclients;

import static io.opentelemetry.javaagent.instrumentation.api.Java8BytecodeBridge.currentContext;
import static io.opentelemetry.javaagent.instrumentation.kafkaclients.KafkaSingletons.consumerReceiveInstrumenter;
import static net.bytebuddy.matcher.ElementMatchers.isPublic;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.field.VirtualField;
import io.opentelemetry.instrumentation.kafka.internal.ReceivedRecords;
import io.opentelemetry.instrumentation.kafka.internal.Timer;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.time.Duration;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.kafka.clients.consumer.ConsumerRecords;

public class KafkaConsumerInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.kafka.clients.consumer.KafkaConsumer");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("poll")
            .and(isPublic())
            .and(takesArguments(1))
            .and(takesArgument(0, long.class).or(takesArgument(0, Duration.class)))
            .and(returns(named("org.apache.kafka.clients.consumer.ConsumerRecords"))),
        this.getClass().getName() + "$PollAdvice");
  }

  @SuppressWarnings("unused")
  public static class PollAdvice {
    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static Timer onEnter() {
      return Timer.start();
    }

    @Advice.OnMethodExit(suppress = Throwable.class, onThrowable = Throwable.class)
    public static void onExit(
        @Advice.Enter Timer timer,
        @Advice.Return ConsumerRecords<?, ?> records,
        @Advice.Thrown Throwable error) {

      // don't create spans when no records were received
      if (records == null || records.isEmpty()) {
        return;
      }

      Context parentContext = currentContext();
      ReceivedRecords receivedRecords = ReceivedRecords.create(records, timer);
      if (consumerReceiveInstrumenter().shouldStart(parentContext, receivedRecords)) {
        Context context = consumerReceiveInstrumenter().start(parentContext, receivedRecords);
        consumerReceiveInstrumenter().end(context, receivedRecords, null, error);

        // we're storing the context of the receive span so that process spans can use it as parent
        // context even though the span has ended
        // this is the suggested behavior according to the spec batch receive scenario:
        // https://github.com/open-telemetry/opentelemetry-specification/blob/main/specification/trace/semantic_conventions/messaging.md#batch-receiving
        VirtualField<ConsumerRecords<?, ?>, Context> consumerRecordsContext =
            VirtualField.find(ConsumerRecords.class, Context.class);
        consumerRecordsContext.set(records, context);
      }
    }
  }
}
