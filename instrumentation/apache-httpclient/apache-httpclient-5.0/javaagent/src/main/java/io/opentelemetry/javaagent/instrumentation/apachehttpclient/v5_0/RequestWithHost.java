/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import java.net.URI;
import java.net.URISyntaxException;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.HttpRequestWrapper;

public class RequestWithHost extends HttpRequestWrapper implements ClassicHttpRequest {

  private final ClassicHttpRequest httpRequest;
  private final URI uri;

  public RequestWithHost(HttpHost httpHost, ClassicHttpRequest httpRequest) {
    super(httpRequest);

    this.httpRequest = httpRequest;

    URI calculatedUri;
    try {
      // combine requested uri with host info
      URI requestUri = httpRequest.getUri();
      calculatedUri =
          new URI(
              httpHost.getSchemeName(),
              null,
              httpHost.getHostName(),
              httpHost.getPort(),
              requestUri.getPath(),
              requestUri.getQuery(),
              requestUri.getFragment());
    } catch (URISyntaxException e) {
      calculatedUri = null;
    }
    uri = calculatedUri;
  }

  @Override
  public URI getUri() throws URISyntaxException {
    if (uri != null) {
      return uri;
    }
    return super.getUri();
  }

  @Override
  public HttpEntity getEntity() {
    return httpRequest.getEntity();
  }

  @Override
  public void setEntity(HttpEntity entity) {
    httpRequest.setEntity(entity);
  }
}
