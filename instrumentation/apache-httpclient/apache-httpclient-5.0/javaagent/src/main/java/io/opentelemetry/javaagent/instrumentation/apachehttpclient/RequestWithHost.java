/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient;

import java.net.URI;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.HttpRequestWrapper;
import org.apache.hc.core5.net.URIAuthority;

public class RequestWithHost extends HttpRequestWrapper implements ClassicHttpRequest {

  private final String scheme;
  private final URIAuthority authority;

  public RequestWithHost(HttpHost httpHost, ClassicHttpRequest httpRequest) {
    super(httpRequest);

    this.scheme = httpHost.getSchemeName();
    this.authority = new URIAuthority(httpHost.getHostName(), httpHost.getPort());
  }

  @Override
  public String getScheme() {
    return scheme;
  }

  @Override
  public URIAuthority getAuthority() {
    return authority;
  }

  @Override
  public URI getUri() {
    // overriding super because it's not correct (doesn't incorporate authority)
    // and isn't needed anyways
    throw new UnsupportedOperationException();
  }

  @Override
  public HttpEntity getEntity() {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setEntity(HttpEntity entity) {
    throw new UnsupportedOperationException();
  }
}
