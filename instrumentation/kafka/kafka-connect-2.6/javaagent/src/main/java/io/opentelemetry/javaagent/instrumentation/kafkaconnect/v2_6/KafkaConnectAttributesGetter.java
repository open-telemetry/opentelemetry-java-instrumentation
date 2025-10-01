/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kafkaconnect.v2_6;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import javax.annotation.Nullable;
import org.apache.kafka.connect.header.Header;
import org.apache.kafka.connect.sink.SinkRecord;

enum KafkaConnectAttributesGetter implements MessagingAttributesGetter<KafkaConnectTask, Void> {
  INSTANCE;

  @Override
  public String getSystem(KafkaConnectTask request) {
    return "kafka";
  }

  @Override
  @Nullable
  public String getDestination(KafkaConnectTask request) {
    return request.getDestinationName();
  }

  @Nullable
  @Override
  public String getDestinationTemplate(KafkaConnectTask request) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(KafkaConnectTask request) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(KafkaConnectTask request) {
    return false;
  }

  @Override
  @Nullable
  public String getConversationId(KafkaConnectTask request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageBodySize(KafkaConnectTask request) {
    return null;
  }

  @Nullable
  @Override
  public Long getMessageEnvelopeSize(KafkaConnectTask request) {
    return null;
  }

  @Override
  @Nullable
  public String getMessageId(KafkaConnectTask request, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String getClientId(KafkaConnectTask request) {
    return null;
  }

  @Nullable
  @Override
  public Long getBatchMessageCount(KafkaConnectTask request, @Nullable Void unused) {
    return (long) request.getRecords().size();
  }

  @Override
  public List<String> getMessageHeader(KafkaConnectTask request, String name) {
    SinkRecord firstRecord = request.getFirstRecord();
    if (firstRecord == null || firstRecord.headers() == null) {
      return Collections.emptyList();
    }

    return StreamSupport.stream(firstRecord.headers().spliterator(), false)
        .filter(header -> name.equals(header.key()) && header.value() != null)
        .map(header -> convertHeaderValue(header))
        .collect(Collectors.toList());
  }

  private static String convertHeaderValue(Header header) {
    Object value = header.value();
    if (value instanceof byte[]) {
      return new String((byte[]) value, StandardCharsets.UTF_8);
    }
    return value.toString();
  }
}
