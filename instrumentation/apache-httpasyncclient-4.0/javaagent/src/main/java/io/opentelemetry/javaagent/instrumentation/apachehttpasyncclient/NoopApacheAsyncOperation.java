/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import io.opentelemetry.instrumentation.api.tracer.NoopHttpClientOperation;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;

public class NoopApacheAsyncOperation extends NoopHttpClientOperation<HttpResponse>
    implements ApacheAsyncOperation {

  private static final NoopApacheAsyncOperation NOOP = new NoopApacheAsyncOperation();

  public static NoopApacheAsyncOperation noop() {
    return NOOP;
  }

  @Override
  public void inject(HttpRequest request) {}
}
