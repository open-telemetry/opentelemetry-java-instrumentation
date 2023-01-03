/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics;
import java.util.List;
import org.apache.hc.core5.http.HttpRequest;

public final class ApacheHttpClientRequest {
  private final Context parentContext;
  private final HttpRequest httpRequest;

  public ApacheHttpClientRequest(Context parentContext, HttpRequest httpRequest) {
    this.parentContext = parentContext;
    this.httpRequest = httpRequest;
  }

  public ApacheHttpClientRequest withHttpRequest(HttpRequest httpRequest) {
    return new ApacheHttpClientRequest(parentContext, httpRequest);
  }

  public BytesTransferMetrics getBytesTransferMetrics() {
    return BytesTransferMetrics.getBytesTransferMetrics(parentContext);
  }

  public String getPeerName() {
    return httpRequest.getAuthority().getHostName();
  }

  public Integer getPeerPort() {
    return ApacheHttpClientAttributesHelper.getPeerPort(httpRequest);
  }

  public String getMethod() {
    return httpRequest.getMethod();
  }

  public String getUrl() {
    return ApacheHttpClientAttributesHelper.getUrl(httpRequest);
  }

  public String getFlavor() {
    return ApacheHttpClientAttributesHelper.getFlavor(httpRequest.getVersion());
  }

  public List<String> getHeader(String name) {
    return ApacheHttpClientAttributesHelper.getHeader(httpRequest, name);
  }

  public String getFirstHeader(String name) {
    return ApacheHttpClientAttributesHelper.getFirstHeader(httpRequest, name);
  }

  public void setHeader(String key, String value) {
    httpRequest.setHeader(key, value);
  }
}
