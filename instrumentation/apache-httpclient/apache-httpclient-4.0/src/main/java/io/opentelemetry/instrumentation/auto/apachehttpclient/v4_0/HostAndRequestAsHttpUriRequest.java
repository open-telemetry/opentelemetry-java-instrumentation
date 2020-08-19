/*
 * Copyright The OpenTelemetry Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.opentelemetry.instrumentation.auto.apachehttpclient.v4_0;

import java.net.URI;
import java.net.URISyntaxException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.ProtocolVersion;
import org.apache.http.RequestLine;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.message.AbstractHttpMessage;

/** Wraps HttpHost and HttpRequest into a HttpUriRequest for decorators and injectors */
public class HostAndRequestAsHttpUriRequest extends AbstractHttpMessage implements HttpUriRequest {

  private final String method;
  private final RequestLine requestLine;
  private final ProtocolVersion protocolVersion;
  private final java.net.URI URI;

  private final HttpRequest actualRequest;

  public HostAndRequestAsHttpUriRequest(HttpHost httpHost, HttpRequest httpRequest) {

    method = httpRequest.getRequestLine().getMethod();
    requestLine = httpRequest.getRequestLine();
    protocolVersion = requestLine.getProtocolVersion();

    URI calculatedURI;
    try {
      calculatedURI = new URI(httpHost.toURI() + httpRequest.getRequestLine().getUri());
    } catch (URISyntaxException e) {
      calculatedURI = null;
    }
    URI = calculatedURI;
    actualRequest = httpRequest;
  }

  @Override
  public void abort() throws UnsupportedOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isAborted() {
    return false;
  }

  @Override
  public void addHeader(String name, String value) {
    actualRequest.addHeader(name, value);
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
  public java.net.URI getURI() {
    return URI;
  }

  public HttpRequest getActualRequest() {
    return actualRequest;
  }
}
