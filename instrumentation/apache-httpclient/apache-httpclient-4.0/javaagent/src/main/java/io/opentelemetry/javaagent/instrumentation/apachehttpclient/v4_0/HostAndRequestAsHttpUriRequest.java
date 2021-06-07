/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.AbstractHttpMessage;
import org.checkerframework.checker.nullness.qual.Nullable;

/** Wraps HttpHost and HttpRequest into a HttpUriRequest for tracers and injectors. */
public final class HostAndRequestAsHttpUriRequest extends AbstractHttpMessage
    implements HttpUriRequest {

  private final String method;
  private final RequestLine requestLine;
  private final ProtocolVersion protocolVersion;
  @Nullable private final URI uri;

  private final HttpRequest actualRequest;

  public HostAndRequestAsHttpUriRequest(HttpHost httpHost, HttpRequest httpRequest) {
    method = httpRequest.getRequestLine().getMethod();
    requestLine = httpRequest.getRequestLine();
    protocolVersion = requestLine.getProtocolVersion();

    URI calculatedUri;
    try {
      calculatedUri = new URI(httpHost.toURI() + httpRequest.getRequestLine().getUri());
    } catch (URISyntaxException e) {
      calculatedUri = null;
    }
    uri = calculatedUri;
    actualRequest = httpRequest;
  }

  @Override
  public void abort() {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isAborted() {
    return false;
  }

  @Override
  public void setHeader(String name, String value) {
    actualRequest.setHeader(name, value);
  }

  @Override
  public String getMethod() {
    return method;
  }

  @Override
  public RequestLine getRequestLine() {
    return requestLine;
  }

  @Override
  public ProtocolVersion getProtocolVersion() {
    return protocolVersion;
  }

  @Override
  public URI getURI() {
    return uri;
  }
}
