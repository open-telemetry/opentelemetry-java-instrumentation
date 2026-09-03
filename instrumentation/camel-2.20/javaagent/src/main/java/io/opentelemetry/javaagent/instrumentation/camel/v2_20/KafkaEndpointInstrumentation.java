/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.camel.v2_20;

import static io.opentelemetry.javaagent.instrumentation.camel.v2_20.CamelMessageTelemetry.messageTelemetry;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.internal.MessagingTelemetryClaims;
import io.opentelemetry.instrumentation.api.util.VirtualField;
import io.opentelemetry.javaagent.bootstrap.messaging.MessagingTelemetryCarrier;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.apache.camel.Exchange;
import org.apache.kafka.clients.consumer.ConsumerRecord;

class KafkaEndpointInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.apache.camel.component.kafka.KafkaEndpoint");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("createKafkaExchange")
            .and(takesArgument(0, named("org.apache.kafka.clients.consumer.ConsumerRecord")))
            .and(returns(named("org.apache.camel.Exchange"))),
        getClass().getName() + "$CreateExchangeAdvice");
  }

  @SuppressWarnings("unused")
  public static class CreateExchangeAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void onExit(
        @Advice.Argument(0) ConsumerRecord<?, ?> record,
        @Advice.Return @Nullable Exchange exchange) {
      if (exchange != null) {
        // the exchange is freshly created for this record, so nothing it could keep is stale
        MessagingTelemetryCarrier<ConsumerRecord<?, ?>> recordTelemetry =
            MessagingTelemetryCarrier.create(
                VirtualField.find(ConsumerRecord.class, MessagingTelemetryClaims.class));
        messageTelemetry().mergeFrom(recordTelemetry, record, exchange.getIn());
      }
    }
  }
}
