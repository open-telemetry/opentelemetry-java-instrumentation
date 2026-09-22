/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkastreams.v0_11;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.argumentSet;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContext;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaConsumerContextUtil;
import io.opentelemetry.instrumentation.kafkaclients.common.v0_11.internal.KafkaProcessRequest;
import java.util.function.BiFunction;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class KafkaStreamsRecordCopyTest {

  @ParameterizedTest
  @MethodSource("deserializers")
  void preservesConsumerContextProcessingSelectionAndDeliveryIdentity(
      BiFunction<ConsumerRecord<?, ?>, ConsumerRecord<?, ?>, ConsumerRecord<?, ?>> deserialize) {
    ConsumerRecord<String, String> incoming = new ConsumerRecord<>("orders", 0, 7, "key", "value");
    ConsumerRecord<String, String> result = new ConsumerRecord<>("orders", 0, 7, "key", "value");
    incoming.headers().add("correlation", "request".getBytes(UTF_8));
    Context context = Context.root();
    KafkaConsumerContextUtil.set(
        incoming, KafkaConsumerContextUtil.create(context, "group", "client"));
    BooleanSupplier rawProcessingSelection = () -> false;
    KafkaConsumerContextUtil.setRawProcessingSelection(incoming, rawProcessingSelection);
    assertThat(KafkaConsumerContextUtil.markConsumedMessageCounted(incoming)).isTrue();

    assertThat(deserialize.apply(incoming, result)).isSameAs(result);
    assertThat(KafkaConsumerContextUtil.markConsumedMessageCounted(result)).isFalse();
    assertThat(KafkaConsumerContextUtil.getRawProcessingSelection(result))
        .isSameAs(rawProcessingSelection);
    KafkaConsumerContext consumerContext = KafkaConsumerContextUtil.get(result);
    assertThat(consumerContext.getContext()).isSameAs(context);
    KafkaProcessRequest request = KafkaProcessRequest.create(consumerContext, result);
    assertThat(request.getConsumerGroup()).isEqualTo("group");
    assertThat(request.getClientId()).isEqualTo("client");
    assertThat(result.topic()).isEqualTo("orders");
    assertThat(result.partition()).isZero();
    assertThat(result.offset()).isEqualTo(7);
    assertThat(result.key()).isEqualTo("key");
    assertThat(result.value()).isEqualTo("value");
    assertThat(result.headers().toArray()).containsExactly(incoming.headers().toArray());
  }

  private static Stream<Arguments> deserializers() {
    BiFunction<ConsumerRecord<?, ?>, ConsumerRecord<?, ?>, ConsumerRecord<?, ?>> sourceNode =
        SourceNodeRecordDeserializerInstrumentation.SaveHeadersAdvice::saveHeaders;
    BiFunction<ConsumerRecord<?, ?>, ConsumerRecord<?, ?>, ConsumerRecord<?, ?>> record =
        RecordDeserializerInstrumentation.DeserializeAdvice::onExit;
    return Stream.of(argumentSet("0.11", sourceNode), argumentSet("1.0+", record));
  }

  @Test
  void bothAdvicesPreserveNullResult() {
    ConsumerRecord<String, String> incoming = new ConsumerRecord<>("orders", 0, 7, "key", "value");
    assertThat(
            SourceNodeRecordDeserializerInstrumentation.SaveHeadersAdvice.saveHeaders(
                incoming, null))
        .isNull();
    assertThat(RecordDeserializerInstrumentation.DeserializeAdvice.onExit(incoming, null)).isNull();
  }

  @Test
  void modernDeserializerKeepsExistingHeaders() {
    ConsumerRecord<String, String> incoming = new ConsumerRecord<>("orders", 0, 7, "key", "value");
    ConsumerRecord<String, String> result = new ConsumerRecord<>("orders", 0, 7, "key", "value");
    incoming.headers().add("correlation", "incoming".getBytes(UTF_8));
    result.headers().add("correlation", "deserialized".getBytes(UTF_8));

    assertThat(RecordDeserializerInstrumentation.DeserializeAdvice.onExit(incoming, result))
        .isSameAs(result);
    assertThat(result.headers().toArray()).hasSize(1);
    assertThat(result.headers().lastHeader("correlation").value())
        .isEqualTo("deserialized".getBytes(UTF_8));
  }
}
