/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons;

import static io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0.commons.ApacheHttpClientAttributesHelper.getUri;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.apachehttpclient.commons.BytesTransferMetrics;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.methods.HttpUriRequest;

public final class ApacheHttpClientRequest {
  private final Context parentContext;
  @Nullable private final URI uri;
  @Nullable private final HttpHost target;
  private final HttpRequest httpRequest;

  private ApacheHttpClientRequest(
      Context parentContext, URI uri, HttpHost target, HttpRequest httpRequest) {
    this.parentContext = parentContext;
    this.uri = uri;
    this.httpRequest = httpRequest;
    this.target = target;
  }

  public ApacheHttpClientRequest(Context parentContext, HttpHost target, HttpRequest httpRequest) {
    this(parentContext, getUri(target, httpRequest), target, httpRequest);
  }

  public ApacheHttpClientRequest(Context parentContext, HttpUriRequest httpRequest) {
    this(parentContext, httpRequest.getURI(), null, httpRequest);
  }

  public ApacheHttpClientRequest withHttpRequest(HttpRequest httpRequest) {
    return new ApacheHttpClientRequest(parentContext, uri, target, httpRequest);
  }

  public BytesTransferMetrics getBytesTransferMetrics() {
    return BytesTransferMetrics.getBytesTransferMetrics(parentContext);
  }

  public String getMethod() {
    return httpRequest.getRequestLine().getMethod();
  }

  public String getUrl() {
    return uri != null ? uri.toString() : null;
  }

  public String getFlavor() {
    return ApacheHttpClientAttributesHelper.getFlavor(httpRequest.getProtocolVersion());
  }

  @Nullable
  public String getPeerName() {
    return uri == null ? null : uri.getHost();
  }

  @Nullable
  public Integer getPeerPort() {
    return ApacheHttpClientAttributesHelper.getPeerPort(uri);
  }

  public List<String> getHeader(String name) {
    return ApacheHttpClientAttributesHelper.getHeader(httpRequest, name);
  }

  public String getFirstHeader(String name) {
    return ApacheHttpClientAttributesHelper.getFirstHeader(httpRequest, name);
  }

  @Nullable
  public InetSocketAddress peerSocketAddress() {
    return ApacheHttpClientAttributesHelper.getPeerSocketAddress(target);
  }
}
