/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
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

  public ApacheHttpClientRequest(HttpHost httpHost, HttpRequest httpRequest) {
    URI calculatedUri = getUri(httpRequest);
    if (calculatedUri != null && httpHost != null) {
      uri = getCalculatedUri(httpHost, calculatedUri);
    } else {
      uri = calculatedUri;
    }
    delegate = httpRequest;
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

  public void setHeader(String name, String value) {
    delegate.setHeader(name, value);
  }

  public String getMethod() {
    return delegate.getRequestLine().getMethod();
  }

  public String getUrl() {
    return uri != null ? uri.toString() : null;
  }

  public String getFlavor() {
    ProtocolVersion protocolVersion = delegate.getProtocolVersion();
    String protocol = protocolVersion.getProtocol();
    if (!protocol.equals("HTTP")) {
      return null;
    }
    int major = protocolVersion.getMajor();
    int minor = protocolVersion.getMinor();
    if (major == 1 && minor == 0) {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_0;
    }
    if (major == 1 && minor == 1) {
      return SemanticAttributes.HttpFlavorValues.HTTP_1_1;
    }
    if (major == 2 && minor == 0) {
      return SemanticAttributes.HttpFlavorValues.HTTP_2_0;
    }
    if (logger.isLoggable(Level.FINE)) {
      logger.fine("unexpected http protocol version: " + protocolVersion);
    }
    return null;
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
        if (logger.isLoggable(Level.FINE)) {
          logger.fine("no default port mapping for scheme: " + uri.getScheme());
        }
        return null;
    }
  }

  @Nullable
  private static URI getUri(HttpRequest httpRequest) {
    try {
      // this can be relative or absolute
      return new URI(httpRequest.getRequestLine().getUri());
    } catch (URISyntaxException e) {
      logger.log(Level.FINE, e.getMessage(), e);
      return null;
    }
  }

  @Nullable
  private static URI getCalculatedUri(HttpHost httpHost, URI uri) {
    try {
      String path = uri.getPath();
      if (!path.startsWith("/")) {
        // elasticsearch RestClient sends relative urls
        // TODO(trask) add test for this and extend to Apache 4, 4.3 and 5
        path = "/" + path;
      }
      return new URI(
          httpHost.getSchemeName(),
          null,
          httpHost.getHostName(),
          httpHost.getPort(),
          path,
          uri.getQuery(),
          uri.getFragment());
    } catch (URISyntaxException e) {
      logger.log(Level.FINE, e.getMessage(), e);
      return null;
    }
  }
}
