/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;

final class SqsProcessRequest extends AbstractSqsRequest {
  private final Request<?> request;
  private final SqsMessage message;

  private SqsProcessRequest(Request<?> request, SqsMessage message) {
    this.request = request;
    this.message = message;
  }

  public static SqsProcessRequest create(Request<?> request, SqsMessage message) {
    return new SqsProcessRequest(request, message);
  }

  @Override
  public Request<?> getRequest() {
    return request;
  }

  public SqsMessage getMessage() {
    return message;
  }
}
