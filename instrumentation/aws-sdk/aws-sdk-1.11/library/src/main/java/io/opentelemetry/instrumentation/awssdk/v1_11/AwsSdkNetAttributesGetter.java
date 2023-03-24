/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import com.amazonaws.Response;
import com.amazonaws.http.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.util.Optional;
import javax.annotation.Nullable;
import org.apache.http.ProtocolVersion;
import org.apache.http.client.methods.HttpRequestBase;

class AwsSdkNetAttributesGetter implements NetClientAttributesGetter<Request<?>, Response<?>> {

  @Override
  public String getTransport(Request<?> request, @Nullable Response<?> response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String getProtocolName(Request<?> request, @Nullable Response<?> response) {
    return Optional.ofNullable(response)
        .map(Response::getHttpResponse)
        .map(HttpResponse::getHttpRequest)
        .map(HttpRequestBase::getProtocolVersion)
        .map(ProtocolVersion::getProtocol)
        .orElse(null);
  }

  @Nullable
  @Override
  public String getProtocolVersion(Request<?> request, @Nullable Response<?> response) {
    return Optional.ofNullable(response)
        .map(Response::getHttpResponse)
        .map(HttpResponse::getHttpRequest)
        .map(HttpRequestBase::getProtocolVersion)
        .map(p -> p.getMajor() + "." + p.getMinor())
        .orElse(null);
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
