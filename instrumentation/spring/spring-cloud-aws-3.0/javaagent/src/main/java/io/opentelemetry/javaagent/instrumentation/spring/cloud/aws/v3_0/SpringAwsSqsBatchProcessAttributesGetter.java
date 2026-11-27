/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.cloud.aws.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.messaging.MessagingAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.AwsSdkHttpAttributesGetter;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsAttributesGetter;
import io.opentelemetry.instrumentation.awssdk.v2_2.internal.SqsMessage;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

class SpringAwsSqsBatchProcessAttributesGetter
    implements MessagingAttributesGetter<SpringAwsSqsBatchProcessRequest, Void>,
        ServerAttributesGetter<SpringAwsSqsBatchProcessRequest> {

  // copied from MessagingIncubatingAttributes.MessagingSystemIncubatingValues
  private static final String AWS_SQS = "aws.sqs";

  // the queue and endpoint are taken from the sdk receive request, as the sdk does
  private static final SqsAttributesGetter sqsAttributesGetter = new SqsAttributesGetter();
  private static final AwsSdkHttpAttributesGetter httpAttributesGetter =
      new AwsSdkHttpAttributesGetter();

  @Override
  public String getSystem(SpringAwsSqsBatchProcessRequest request) {
    return AWS_SQS;
  }

  @Nullable
  @Override
  public String getDestination(SpringAwsSqsBatchProcessRequest request) {
    return sqsAttributesGetter.getDestination(request.getReceiveRequest());
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
    return (long) request.getMessageCount();
  }

  // headers are the sqs message attributes, not the spring message headers, which also carry
  // framework data such as the receipt handle and the original sdk message
  @Override
  public List<String> getMessageHeader(SpringAwsSqsBatchProcessRequest request, String name) {
    List<String> values = new ArrayList<>();
    for (SqsMessage message : request.getSqsMessages()) {
      String value = message.getMessageAttribute(name);
      if (value != null) {
        values.add(value);
      }
    }
    return values;
  }

  @Override
  public Iterable<String> getMessageHeaderNames(SpringAwsSqsBatchProcessRequest request) {
    Set<String> names = new LinkedHashSet<>();
    for (SqsMessage message : request.getSqsMessages()) {
      names.addAll(message.getMessageAttributeNames());
    }
    return names;
  }

  @Nullable
  @Override
  public String getServerAddress(SpringAwsSqsBatchProcessRequest request) {
    return httpAttributesGetter.getServerAddress(request.getReceiveRequest());
  }

  @Nullable
  @Override
  public Integer getServerPort(SpringAwsSqsBatchProcessRequest request) {
    return httpAttributesGetter.getServerPort(request.getReceiveRequest());
  }
}
