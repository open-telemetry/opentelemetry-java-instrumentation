/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient.v4_1;

import java.net.URI;
import org.apache.http.client.methods.HttpRequestBase;

class HttpUriRequest extends HttpRequestBase {

  private final String methodName;

  HttpUriRequest(String methodName, URI uri) {
    this.methodName = methodName;
    setURI(uri);
  }

  @Override
  public String getMethod() {
    return methodName;
  }
}
