/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import com.amazonaws.Request;
import java.util.List;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SqsReceiveRequest extends AbstractSqsRequest {
  private final Request<?> request;
  private final List<SqsMessage> messages;

  static SqsReceiveRequest create(Request<?> request, List<SqsMessage> messages) {
    return new SqsReceiveRequest(request, messages);
  }

  private SqsReceiveRequest(Request<?> request, List<SqsMessage> messages) {
    this.request = request;
    this.messages = messages;
  }

  @Override
  Request<?> getRequest() {
    return request;
  }

  List<SqsMessage> getMessages() {
    return messages;
  }
}
