/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import static java.util.logging.Level.FINE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;

public final class ApacheHttpClientRequest {

  private static final Logger logger = Logger.getLogger(ApacheHttpClientRequest.class.getName());

  @Nullable private final URI uri;

  private final HttpRequest delegate;
  private final EntityDetails entityDetails;

  public ApacheHttpClientRequest(HttpRequest httpRequest, EntityDetails entityDetails) {
    uri = getUri(httpRequest);
    this.entityDetails = entityDetails;
    this.delegate = httpRequest;
  }

  public List<String> getHeader(String name) {
    return headersToList(delegate.getHeaders(name));
  }

  // minimize memory overhead by not using streams
  static List<String> headersToList(Header[] headers) {
    if (headers.length == 0) {
      return Collections.emptyList();
    }
    List<String> headersList = new ArrayList<>(headers.length);
    for (int i = 0; i < headers.length; ++i) {
      headersList.add(headers[i].getValue());
    }
    return headersList;
  }

  public Long requestContentLength() {
    return entityDetails != null ? entityDetails.getContentLength() : null;
  }

  public void setHeader(String name, String value) {
    delegate.setHeader(name, value);
  }

  public String getMethod() {
    return delegate.getMethod();
  }

  public String getUrl() {
    return uri != null ? uri.toString() : null;
  }

  public String getPeerName() {
    return uri != null ? uri.getHost() : null;
  }

  public Integer getPeerPort() {
    if (uri == null) {
      return null;
    }
    int port = uri.getPort();
    if (port != -1) {
      return port;
    }
    switch (uri.getScheme()) {
      case "http":
        return 80;
      case "https":
        return 443;
      default:
        logger.log(FINE, "no default port mapping for scheme: {}", uri.getScheme());
        return null;
    }
  }

  @Nullable
  private static URI getUri(HttpRequest httpRequest) {
    try {
      // this can be relative or absolute
      return httpRequest.getUri();
    } catch (URISyntaxException e) {
      logger.log(FINE, e.getMessage(), e);
      return null;
    }
  }
}
