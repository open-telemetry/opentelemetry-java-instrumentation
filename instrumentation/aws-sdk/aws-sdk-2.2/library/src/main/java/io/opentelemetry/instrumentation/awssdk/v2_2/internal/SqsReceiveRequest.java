/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import java.util.List;
import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SqsReceiveRequest extends AbstractSqsRequest {
  private final ExecutionAttributes request;
  private final List<SqsMessage> messages;

  private SqsReceiveRequest(ExecutionAttributes request, List<SqsMessage> messages) {
    this.request = request;
    this.messages = messages;
  }

  public static SqsReceiveRequest create(ExecutionAttributes request, List<SqsMessage> messages) {
    return new SqsReceiveRequest(request, messages);
  }

  @Override
  public ExecutionAttributes getRequest() {
    return request;
  }

  public List<SqsMessage> getMessages() {
    return messages;
  }
}
