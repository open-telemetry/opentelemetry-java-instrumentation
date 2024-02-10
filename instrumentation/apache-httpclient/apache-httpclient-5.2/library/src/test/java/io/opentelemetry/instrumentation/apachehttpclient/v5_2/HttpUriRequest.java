/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v5_2;

import java.net.URI;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;

final class HttpUriRequest extends HttpUriRequestBase {

  private static final long serialVersionUID = 1L;

  private final String methodName;

  HttpUriRequest(String methodName, URI uri) {
    super(methodName, uri);
    this.methodName = methodName;
    setUri(uri);
  }

  @Override
  public String getMethod() {
    return methodName;
  }
}
