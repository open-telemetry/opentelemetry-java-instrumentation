/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;
import org.apache.hc.core5.http.HttpRequest;
import org.apache.hc.core5.http.HttpResponse;
import org.apache.hc.core5.http.ProtocolVersion;

final class ApacheHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<HttpRequest, HttpResponse> {

  @Nullable
  @Override
  public String getProtocolName(HttpRequest request, @Nullable HttpResponse response) {
    ProtocolVersion protocolVersion = getVersion(request, response);
    if (protocolVersion == null) {
      return null;
    }
    return protocolVersion.getProtocol();
  }

  @Nullable
  @Override
  public String getProtocolVersion(HttpRequest request, @Nullable HttpResponse response) {
    ProtocolVersion protocolVersion = getVersion(request, response);
    if (protocolVersion == null) {
      return null;
    }
    return protocolVersion.getMajor() + "." + protocolVersion.getMinor();
  }

  @Override
  @Nullable
  public String getPeerName(HttpRequest request) {
    return request.getAuthority().getHostName();
  }

  @Override
  public Integer getPeerPort(HttpRequest request) {
    return request.getAuthority().getPort();
  }

  private static ProtocolVersion getVersion(HttpRequest request, @Nullable HttpResponse response) {
    ProtocolVersion protocolVersion = request.getVersion();
    if (protocolVersion == null && response != null) {
      protocolVersion = response.getVersion();
    }
    return protocolVersion;
  }
}
