/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons;

import io.opentelemetry.context.Context;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.HttpUriRequest;

public final class ApacheHttpClientRequest {
  @Nullable private final URI uri;
  @Nullable private final HttpHost target;
  private final HttpRequest httpRequest;
  private final Context parentContext;

  public ApacheHttpClientRequest(
      Context parentContext, @Nullable HttpHost target, HttpRequest httpRequest) {
    this.parentContext = parentContext;
    this.uri = ApacheHttpClientUtils.getUri(target, httpRequest);
    this.httpRequest = httpRequest;
    this.target = target;
  }

  public ApacheHttpClientRequest(Context parentContext, HttpUriRequest httpRequest) {
    this.parentContext = parentContext;
    this.uri = httpRequest.getURI();
    this.httpRequest = httpRequest;
    this.target = null;
  }

  public Context getParentContext() {
    return parentContext;
  }

  public List<String> getHeader(String name) {
    return ApacheHttpClientUtils.getHeader(httpRequest, name);
  }

  public void setHeader(String name, String value) {
    httpRequest.setHeader(name, value);
  }

  public String getMethod() {
    return httpRequest.getRequestLine().getMethod();
  }

  public String getUrl() {
    return uri != null ? uri.toString() : null;
  }

  public String getFlavor() {
    return ApacheHttpClientUtils.getFlavor(httpRequest.getProtocolVersion());
  }

  @Nullable
  public String getPeerName() {
    return uri == null ? null : uri.getHost();
  }

  @Nullable
  public Integer getPeerPort() {
    return ApacheHttpClientUtils.getPeerPort(uri);
  }

  @Nullable
  public InetSocketAddress peerSocketAddress() {
    return ApacheHttpClientUtils.getPeerSocketAddress(target);
  }
}
