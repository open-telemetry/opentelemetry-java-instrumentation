/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import static java.util.logging.Level.FINE;

import io.opentelemetry.context.Context;
import java.util.List;
import java.util.logging.Logger;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.net.URIAuthority;

public class ApacheHttpClientRequest {
  private static final Logger logger = Logger.getLogger(ApacheHttpClientResponse.class.getName());

  private final Context parentContext;
  private final HttpRequest httpRequest;

  public ApacheHttpClientRequest(Context parentContext, HttpRequest httpRequest) {
    this.parentContext = parentContext;
    this.httpRequest = httpRequest;
  }

  public Context getParentContext() {
    return parentContext;
  }

  public String getPeerName() {
    return httpRequest.getAuthority().getHostName();
  }

  public Integer getPeerPort() {
    int port = httpRequest.getAuthority().getPort();
    if (port != -1) {
      return port;
    }
    String scheme = httpRequest.getScheme();
    if (scheme == null) {
      return 80;
    }
    switch (scheme) {
      case "http":
        return 80;
      case "https":
        return 443;
      default:
        logger.log(FINE, "no default port mapping for scheme: {0}", scheme);
        return null;
    }
  }

  public String getMethod() {
    return httpRequest.getMethod();
  }

  public String getUrl() {
    // similar to org.apache.hc.core5.http.message.BasicHttpRequest.getUri()
    // not calling getUri() to avoid unnecessary conversion
    StringBuilder url = new StringBuilder();
    URIAuthority authority = httpRequest.getAuthority();
    if (authority != null) {
      String scheme = httpRequest.getScheme();
      if (scheme != null) {
        url.append(scheme);
        url.append("://");
      } else {
        url.append("http://");
      }
      url.append(authority.getHostName());
      int port = authority.getPort();
      if (port >= 0) {
        url.append(":");
        url.append(port);
      }
    }
    String path = httpRequest.getPath();
    if (path != null) {
      if (url.length() > 0 && !path.startsWith("/")) {
        url.append("/");
      }
      url.append(path);
    } else {
      url.append("/");
    }
    return url.toString();
  }

  public String getFlavor() {
    return ApacheHttpClientHelper.getFlavor(httpRequest.getVersion());
  }

  public List<String> getHeader(String name) {
    return ApacheHttpClientHelper.getHeader(httpRequest, name);
  }

  public void setHeader(String key, String value) {
    httpRequest.setHeader(key, value);
  }
}
