/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import java.util.Collection;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SqsCreateRequest {

  @Nullable private final String queueUrl;
  private final Map<String, String> messageAttributes;

  SqsCreateRequest(@Nullable String queueUrl, Map<String, String> messageAttributes) {
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
    return messageAttributes.get(name);
  }

  Collection<String> getMessageAttributeNames() {
    return messageAttributes.keySet();
  }
}
