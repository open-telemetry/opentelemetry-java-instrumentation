/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.openai.v1_1;

import com.openai.models.responses.ResponseCreateParams;

final class ResponseCreateRequest {

  private final ResponseCreateParams request;
  private final boolean streaming;

  static ResponseCreateRequest create(ResponseCreateParams request) {
    return new ResponseCreateRequest(request, false);
  }

  static ResponseCreateRequest createStreaming(ResponseCreateParams request) {
    return new ResponseCreateRequest(request, true);
  }

  private ResponseCreateRequest(ResponseCreateParams request, boolean streaming) {
    this.request = request;
    this.streaming = streaming;
  }

  ResponseCreateParams getRequest() {
    return request;
  }

  boolean isStreaming() {
    return streaming;
  }
}
