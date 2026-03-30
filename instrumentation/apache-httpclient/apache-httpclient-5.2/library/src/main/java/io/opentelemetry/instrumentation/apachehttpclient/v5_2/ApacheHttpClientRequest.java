/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v5_2;

import static java.util.logging.Level.FINE;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.HttpRequest;

public final class ApacheHttpClientRequest {

  private static final Logger logger = Logger.getLogger(ApacheHttpClientRequest.class.getName());

  @Nullable private final URI uri;
  private final HttpRequest delegate;
  @Nullable private final HttpHost target;

  ApacheHttpClientRequest(@Nullable HttpHost httpHost, HttpRequest httpRequest) {
    URI calculatedUri = getUri(httpRequest);
    if (calculatedUri != null && httpHost != null) {
      uri = getCalculatedUri(httpHost, calculatedUri);
    } else {
      uri = calculatedUri;
    }
    delegate = httpRequest;
    target = httpHost;
  }

  /** Returns the actual {@link HttpRequest} being executed by the client. */
  public HttpRequest getRequest() {
    return delegate;
  }

  String getMethod() {
    return delegate.getMethod();
  }

  @Nullable
  String getUrl() {
    return uri != null ? uri.toString() : null;
  }

  @Nullable
  String getScheme() {
    if (uri != null) {
      return uri.getScheme();
    }
    if (target != null) {
      return target.getSchemeName();
    }
    return null;
  }

  @Nullable
  String getServerAddress() {
    if (uri != null) {
      return uri.getHost();
    }
    if (target != null) {
      return target.getHostName();
    }
    return null;
  }

  @Nullable
  Integer getServerPort() {
    if (uri != null) {
      return uri.getPort();
    }
    if (target != null) {
      return target.getPort();
    }
    return null;
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

  @Nullable
  private static URI getCalculatedUri(HttpHost httpHost, URI uri) {
    try {
      return new URI(
          httpHost.getSchemeName(),
          uri.getUserInfo(),
          httpHost.getHostName(),
          httpHost.getPort(),
          uri.getPath(),
          uri.getQuery(),
          uri.getFragment());
    } catch (URISyntaxException e) {
      logger.log(FINE, e.getMessage(), e);
      return null;
    }
  }
}
