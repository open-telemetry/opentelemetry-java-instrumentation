/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v5_0;

import java.net.URI;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;

final class Http5UriRequest extends HttpUriRequestBase {

  private static final long serialVersionUID = 1L;

  private final String methodName;

  Http5UriRequest(String methodName, URI uri) {
    super(methodName, uri);
    this.methodName = methodName;
    setUri(uri);
  }

  @Override
  public String getMethod() {
    return methodName;
  }
}
