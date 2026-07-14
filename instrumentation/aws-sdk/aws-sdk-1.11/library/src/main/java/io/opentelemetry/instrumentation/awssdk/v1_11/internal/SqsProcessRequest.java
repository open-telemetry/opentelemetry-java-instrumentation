/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11.internal;

import com.amazonaws.Request;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class SqsProcessRequest extends AbstractSqsRequest {
  private final Request<?> request;
  private final SqsMessage message;

  static SqsProcessRequest create(Request<?> request, SqsMessage message) {
    return new SqsProcessRequest(request, message);
  }

  private SqsProcessRequest(Request<?> request, SqsMessage message) {
    this.request = request;
    this.message = message;
  }

  @Override
  Request<?> getRequest() {
    return request;
  }

  SqsMessage getMessage() {
    return message;
  }
}
