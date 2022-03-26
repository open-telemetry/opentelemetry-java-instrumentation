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
import javax.annotation.Nullable;
import org.apache.hc.core5.http.EntityDetails;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.ProtocolVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ApacheHttpClientRequest {

  private static final Logger logger = LoggerFactory.getLogger(ApacheHttpClientRequest.class);

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

  public long requestContentLength() {
    return entityDetails.getContentLength();
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

  public String getFlavor() {
    ProtocolVersion protocolVersion = delegate.getVersion();
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

  @Nullable
  private static URI getUri(HttpRequest httpRequest) {
    try {
      // this can be relative or absolute
      return httpRequest.getUri();
    } catch (URISyntaxException e) {
      logger.debug(e.getMessage(), e);
      return null;
    }
  }
}
