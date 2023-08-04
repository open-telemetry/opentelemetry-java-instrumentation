/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.http.HttpClientAttributesGetter;
import java.util.List;
import javax.annotation.Nullable;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.HttpRequestBase;

class AwsSdkHttpAttributesGetter implements HttpClientAttributesGetter<Request<?>, Response<?>> {

  @Override
  public String getUrlFull(Request<?> request) {
    return request.getEndpoint().toString();
  }

  @Override
  public String getHttpRequestMethod(Request<?> request) {
    return request.getHttpMethod().name();
  }

  @Override
  public List<String> getHttpRequestHeader(Request<?> request, String name) {
    String value = request.getHeaders().get(name.equals("user-agent") ? "User-Agent" : name);
    return value == null ? emptyList() : singletonList(value);
  }

  @Override
  public Integer getHttpResponseStatusCode(
      Request<?> request, Response<?> response, @Nullable Throwable error) {
    return response.getHttpResponse().getStatusCode();
  }

  @Override
  public List<String> getHttpResponseHeader(Request<?> request, Response<?> response, String name) {
    String value = response.getHttpResponse().getHeaders().get(name);
    return value == null ? emptyList() : singletonList(value);
  }

  @Nullable
  @Override
  public String getNetworkProtocolName(Request<?> request, @Nullable Response<?> response) {
    ProtocolVersion protocolVersion = getProtocolVersion(response);
    if (protocolVersion == null) {
      return null;
    }
    return protocolVersion.getProtocol();
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(Request<?> request, @Nullable Response<?> response) {
    ProtocolVersion protocolVersion = getProtocolVersion(response);
    if (protocolVersion == null) {
      return null;
    }
    if (protocolVersion.getMinor() == 0) {
      return Integer.toString(protocolVersion.getMajor());
    }
    return protocolVersion.getMajor() + "." + protocolVersion.getMinor();
  }

  @Nullable
  private static ProtocolVersion getProtocolVersion(@Nullable Response<?> response) {
    if (response == null) {
      return null;
    }
    HttpResponse httpResponse = response.getHttpResponse();
    if (httpResponse == null) {
      return null;
    }
    HttpRequestBase httpRequest = httpResponse.getHttpRequest();
    if (httpRequest == null) {
      return null;
    }
    return httpRequest.getProtocolVersion();
  }

  @Override
  @Nullable
  public String getServerAddress(Request<?> request) {
    return request.getEndpoint().getHost();
  }

  @Override
  public Integer getServerPort(Request<?> request) {
    return request.getEndpoint().getPort();
  }
}
