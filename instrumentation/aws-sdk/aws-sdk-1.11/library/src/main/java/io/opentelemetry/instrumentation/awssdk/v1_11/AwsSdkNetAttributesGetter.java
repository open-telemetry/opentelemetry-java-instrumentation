/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.HttpRequestBase;

class AwsSdkNetAttributesGetter implements NetClientAttributesGetter<Request<?>, Response<?>> {

  @Nullable
  @Override
  public String getProtocolName(Request<?> request, @Nullable Response<?> response) {
    ProtocolVersion protocolVersion = getProtocolVersion(response);
    if (protocolVersion == null) {
      return null;
    }
    return protocolVersion.getProtocol();
  }

  @Nullable
  @Override
  public String getProtocolVersion(Request<?> request, @Nullable Response<?> response) {
    ProtocolVersion protocolVersion = getProtocolVersion(response);
    if (protocolVersion == null) {
      return null;
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
  public String getPeerName(Request<?> request) {
    return request.getEndpoint().getHost();
  }

  @Override
  public Integer getPeerPort(Request<?> request) {
    return request.getEndpoint().getPort();
  }
}
