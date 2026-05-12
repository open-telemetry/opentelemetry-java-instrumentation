/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import java.net.URI;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.message.HttpRequestWrapper;
import org.apache.hc.core5.net.URIAuthority;

public class RequestWithHost extends HttpRequestWrapper implements ClassicHttpRequest {

  @Nullable private final String scheme;
  @Nullable private final URIAuthority authority;

  public RequestWithHost(@Nullable HttpHost httpHost, ClassicHttpRequest httpRequest) {
    super(httpRequest);
    if (httpHost != null) {
      this.scheme = httpHost.getSchemeName();
      this.authority = new URIAuthority(httpHost.getHostName(), httpHost.getPort());
    } else {
      this.scheme = httpRequest.getScheme();
      this.authority = httpRequest.getAuthority();
    }
  }

  @Override
  @Nullable
  public String getScheme() {
    return scheme;
  }

  @Override
  @Nullable
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
