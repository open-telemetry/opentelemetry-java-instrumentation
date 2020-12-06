/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;

public interface HttpUrlConnectionOperation extends HttpClientOperation<HttpUrlResponse> {

  static HttpUrlConnectionOperation noop() {
    return NoopHttpUrlConnectionOperation.noop();
  }

  boolean finished();
}
