/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.joddhttp.v4_2;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;
import jodd.http.HttpRequest;
import jodd.http.HttpResponse;

final class JoddHttpNetAttributesGetter
    implements NetClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  public String getNetworkProtocolName(HttpRequest request, @Nullable HttpResponse response) {
    return "http";
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(HttpRequest request, @Nullable HttpResponse response) {
    String httpVersion = request.httpVersion();
    if (httpVersion == null && response != null) {
      httpVersion = response.httpVersion();
    }
    if (httpVersion != null) {
      if (httpVersion.contains("/")) {
        httpVersion = httpVersion.substring(httpVersion.lastIndexOf("/") + 1);
      }
    }
    return httpVersion;
  }

  @Override
  @Nullable
  public String getServerAddress(HttpRequest request) {
    return request.host();
  }

  @Override
  public Integer getServerPort(HttpRequest request) {
    return request.port();
  }
}
