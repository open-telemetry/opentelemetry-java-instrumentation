/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awslambdaevents.common.v2_2.internal;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.amazonaws.services.lambda.runtime.events.SQSEvent.MessageAttribute;
import com.amazonaws.services.lambda.runtime.events.SQSEvent.SQSMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

final class SqsMessageAttributesGetter extends SqsAttributesGetter<SQSMessage, Boolean> {

  @Nullable
  @Override
  public String getDestination(SQSMessage message) {
    return queueName(message);
  }

  @Nullable
  @Override
  public String getMessageId(SQSMessage message, @Nullable Boolean unused) {
    return message.getMessageId();
  }

  @Override
  public List<String> getMessageHeader(SQSMessage message, String name) {
    Map<String, MessageAttribute> attributes = message.getMessageAttributes();
    MessageAttribute attribute = attributes == null ? null : attributes.get(name);
    String value = attribute == null ? null : attribute.getStringValue();
    return value == null ? emptyList() : singletonList(value);
  }

  @Override
  public Iterable<String> getMessageHeaderNames(SQSMessage message) {
    Map<String, MessageAttribute> attributes = message.getMessageAttributes();
    return attributes == null ? emptyList() : new ArrayList<>(attributes.keySet());
  }
}
