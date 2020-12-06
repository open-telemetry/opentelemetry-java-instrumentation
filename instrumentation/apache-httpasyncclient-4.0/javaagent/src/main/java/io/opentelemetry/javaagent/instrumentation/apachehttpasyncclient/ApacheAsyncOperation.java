/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;

public interface ApacheAsyncOperation extends HttpClientOperation<HttpResponse> {

  static ApacheAsyncOperation noop() {
    return NoopApacheAsyncOperation.noop();
  }

  void inject(HttpRequest request);
}
