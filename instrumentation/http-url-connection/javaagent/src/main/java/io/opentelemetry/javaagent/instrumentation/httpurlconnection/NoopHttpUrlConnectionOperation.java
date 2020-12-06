/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpurlconnection;

import io.opentelemetry.instrumentation.api.tracer.NoopHttpClientOperation;

public class NoopHttpUrlConnectionOperation extends NoopHttpClientOperation<HttpUrlResponse>
    implements HttpUrlConnectionOperation {

  private static final NoopHttpUrlConnectionOperation NOOP = new NoopHttpUrlConnectionOperation();

  public static NoopHttpUrlConnectionOperation noop() {
    return NOOP;
  }

  @Override
  public boolean finished() {
    return true;
  }
}
