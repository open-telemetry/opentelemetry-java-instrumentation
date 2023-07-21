/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jaxrsclient.v1_1;

import static java.util.Collections.emptyList;

import com.sun.jersey.api.client.ClientRequest;
import com.sun.jersey.api.client.ClientResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

final class JaxRsClientHttpAttributesGetter
    implements HttpClientAttributesGetter<ClientRequest, ClientResponse> {

  @Override
  @Nullable
  public String getHttpRequestMethod(ClientRequest httpRequest) {
    return httpRequest.getMethod();
  }

  @Override
  public String getUrlFull(ClientRequest httpRequest) {
    return httpRequest.getURI().toString();
  }

  @Override
  public List<String> getHttpRequestHeader(ClientRequest httpRequest, String name) {
    List<Object> rawHeaders = httpRequest.getHeaders().getOrDefault(name, emptyList());
    if (rawHeaders.isEmpty()) {
      return emptyList();
    }
    List<String> stringHeaders = new ArrayList<>(rawHeaders.size());
    for (Object headerValue : rawHeaders) {
      stringHeaders.add(String.valueOf(headerValue));
    }
    return stringHeaders;
  }

  @Override
  public Integer getHttpResponseStatusCode(
      ClientRequest httpRequest, ClientResponse httpResponse, @Nullable Throwable error) {
    return httpResponse.getStatus();
  }

  @Override
  public List<String> getHttpResponseHeader(
      ClientRequest httpRequest, ClientResponse httpResponse, String name) {
    return httpResponse.getHeaders().getOrDefault(name, emptyList());
  }

  @Override
  @Nullable
  public String getServerAddress(ClientRequest request) {
    return request.getURI().getHost();
  }

  @Override
  public Integer getServerPort(ClientRequest request) {
    return request.getURI().getPort();
  }
}
