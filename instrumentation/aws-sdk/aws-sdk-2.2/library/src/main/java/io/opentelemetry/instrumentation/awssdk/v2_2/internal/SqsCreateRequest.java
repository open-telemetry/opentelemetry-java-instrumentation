/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class SqsCreateRequest {

  @Nullable private final String queueUrl;
  private final Map<String, MessageAttributeValue> messageAttributes;

  SqsCreateRequest(
      @Nullable String queueUrl, Map<String, MessageAttributeValue> messageAttributes) {
    this.queueUrl = queueUrl;
    this.messageAttributes = messageAttributes;
  }

  @Nullable
  String getDestination() {
    if (queueUrl == null) {
      return null;
    }
    int i = queueUrl.lastIndexOf('/');
    return i > 0 ? queueUrl.substring(i + 1) : null;
  }

  @Nullable
  String getMessageAttribute(String name) {
    MessageAttributeValue value = messageAttributes.get(name);
    return value != null ? value.stringValue() : null;
  }

  Collection<String> getMessageAttributeNames() {
    return messageAttributes.keySet();
  }
}
