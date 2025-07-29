/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v2_2.internal;

import software.amazon.awssdk.core.interceptor.ExecutionAttributes;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SqsProcessRequest extends AbstractSqsRequest {
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
