/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpasyncclient;

import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.Header;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ApacheHttpClientRequest {

  private static final Logger logger = LoggerFactory.getLogger(ApacheHttpClientRequest.class);

  @Nullable private final URI uri;

  private final HttpRequest delegate;

  public ApacheHttpClientRequest(HttpRequest httpRequest) {
    URI calculatedUri;
    if (httpRequest instanceof HttpUriRequest) {
      // Note: this is essentially an optimization: HttpUriRequest allows quicker access to required
      // information. The downside is that we need to load HttpUriRequest which essentially means we
      // depend on httpasyncclient library depending on httpclient library. Currently this seems to
      // be the case.
      calculatedUri = ((HttpUriRequest) httpRequest).getURI();
    } else {
      RequestLine requestLine = httpRequest.getRequestLine();
      try {
        calculatedUri = new URI(requestLine.getUri());
      } catch (URISyntaxException e) {
        logger.debug(e.getMessage(), e);
        calculatedUri = null;
      }
    }
    uri = calculatedUri;
    delegate = httpRequest;
  }

  public String getHeader(String name) {
    Header header = delegate.getFirstHeader(name);
    return header != null ? header.getValue() : null;
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

  public String getTarget() {
    if (uri == null) {
      return null;
    }
    String pathString = uri.getPath();
    String queryString = uri.getQuery();
    if (pathString != null && queryString != null) {
      return pathString + "?" + queryString;
    } else if (queryString != null) {
      return "?" + queryString;
    } else {
      return pathString;
    }
  }

  public String getHost() {
    Header header = delegate.getFirstHeader("Host");
    if (header != null) {
      return header.getValue();
    }
    return null;
  }

  public String getScheme() {
    return uri != null ? uri.getScheme() : null;
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
    logger.debug("unexpected http protocol version: " + protocolVersion);
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
        logger.debug("no default port mapping for scheme: {}", uri.getScheme());
        return null;
    }
  }
}
