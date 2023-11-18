/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2;

import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

final class SqsProcessRequest extends AbstractSqsRequest {
  private final ExecutionAttributes request;
  private final SqsMessage message;

  private SqsProcessRequest(ExecutionAttributes request, SqsMessage message) {
    this.request = request;
    this.message = message;
  }

  public static SqsProcessRequest create(ExecutionAttributes request, SqsMessage message) {
    return new SqsProcessRequest(request, message);
  }

  @Override
  public ExecutionAttributes getRequest() {
    return request;
  }

  public SqsMessage getMessage() {
    return message;
  }
}
