/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import io.awspring.cloud.sqs.listener.SqsHeaders;
import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.springframework.messaging.Message;

class SpringAwsSqsBatchProcessAttributesGetter
    implements MessagingAttributesGetter<SpringAwsSqsBatchProcessRequest, Void> {

  // copied from MessagingIncubatingAttributes.MessagingSystemIncubatingValues
  private static final String AWS_SQS = "aws_sqs";

  @Override
  public String getSystem(SpringAwsSqsBatchProcessRequest request) {
    return AWS_SQS;
  }

  @Nullable
  @Override
  public String getDestination(SpringAwsSqsBatchProcessRequest request) {
    // all messages of a batch come from the same queue
    for (Message<?> message : request.getMessages()) {
      Object queueName = message.getHeaders().get(SqsHeaders.SQS_QUEUE_NAME_HEADER);
      if (queueName != null) {
        return queueName.toString();
      }
    }
    return null;
  }

  @Nullable
  @Override
  public String getDestinationTemplate(SpringAwsSqsBatchProcessRequest request) {
    return null;
  }

  @Override
  public boolean isTemporaryDestination(SpringAwsSqsBatchProcessRequest request) {
    return false;
  }

  @Override
  public boolean isAnonymousDestination(SpringAwsSqsBatchProcessRequest request) {
    return false;
  }

  @Nullable
  @Override
  public String getConversationId(SpringAwsSqsBatchProcessRequest request) {
    return null;
  }

  @Nullable
  @Override
  public String getMessageId(SpringAwsSqsBatchProcessRequest request, @Nullable Void unused) {
    return null;
  }

  @Nullable
  @Override
  public String getClientId(SpringAwsSqsBatchProcessRequest request) {
    return null;
  }

  @Override
  public Long getBatchMessageCount(SpringAwsSqsBatchProcessRequest request, @Nullable Void unused) {
    return (long) request.getMessages().size();
  }

  @Override
  public List<String> getMessageHeader(SpringAwsSqsBatchProcessRequest request, String name) {
    List<String> values = new ArrayList<>();
    for (Message<?> message : request.getMessages()) {
      Object value = message.getHeaders().get(name);
      if (value != null) {
        values.add(value.toString());
      }
    }
    return values;
  }

  @Override
  public Iterable<String> getMessageHeaderNames(SpringAwsSqsBatchProcessRequest request) {
    Set<String> names = new LinkedHashSet<>();
    for (Message<?> message : request.getMessages()) {
      names.addAll(message.getHeaders().keySet());
    }
    return names;
  }
}
