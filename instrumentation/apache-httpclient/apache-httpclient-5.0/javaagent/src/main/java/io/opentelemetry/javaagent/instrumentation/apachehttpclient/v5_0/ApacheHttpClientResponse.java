/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import java.util.List;
import org.apache.hc.core5.http.HttpResponse;

public class ApacheHttpClientResponse {
  private final HttpResponse httpResponse;

  public ApacheHttpClientResponse(HttpResponse httpResponse) {
    this.httpResponse = httpResponse;
  }

  public int getStatusCode() {
    return httpResponse.getCode();
  }

  public String getFlavor() {
    return ApacheHttpClientHelper.getFlavor(httpResponse.getVersion());
  }

  public List<String> getHeader(String name) {
    return ApacheHttpClientHelper.getHeader(httpResponse, name);
  }
}
