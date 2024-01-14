/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3;

import static java.util.logging.Level.FINE;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.apache.http.Header;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;

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
  public HttpRequest getDelegate() {
    return delegate;
  }

  List<String> getHeader(String name) {
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

  void setHeader(String name, String value) {
    delegate.setHeader(name, value);
  }

  String getMethod() {
    return delegate.getRequestLine().getMethod();
  }

  @Nullable
  String getUrl() {
    return uri != null ? uri.toString() : null;
  }

  String getProtocolName() {
    return delegate.getProtocolVersion().getProtocol();
  }

  String getProtocolVersion() {
    ProtocolVersion protocolVersion = delegate.getProtocolVersion();
    if (protocolVersion.getMinor() == 0) {
      return Integer.toString(protocolVersion.getMajor());
    }
    return protocolVersion.getMajor() + "." + protocolVersion.getMinor();
  }

  @Nullable
  public String getServerAddress() {
    return uri == null ? null : uri.getHost();
  }

  @Nullable
  public Integer getServerPort() {
    return uri == null ? null : uri.getPort();
  }

  @Nullable
  private static URI getUri(HttpRequest httpRequest) {
    try {
      // this can be relative or absolute
      return new URI(httpRequest.getRequestLine().getUri());
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
          null,
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

  @Nullable
  public InetSocketAddress getNetworkPeerAddress() {
    if (target == null) {
      return null;
    }
    InetAddress inetAddress = target.getAddress();
    return inetAddress == null ? null : new InetSocketAddress(inetAddress, target.getPort());
  }
}
