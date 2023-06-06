/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import akka.http.scaladsl.model.Uri;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpUtil;
import javax.annotation.Nullable;

class AkkaNetServerAttributesGetter
    implements NetServerAttributesGetter<HttpRequest, HttpResponse> {

  @Nullable
  @Override
  public String getNetworkProtocolName(HttpRequest request, @Nullable HttpResponse httpResponse) {
    return AkkaHttpUtil.protocolName(request);
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      HttpRequest request, @Nullable HttpResponse httpResponse) {
    return AkkaHttpUtil.protocolVersion(request);
  }

  @Nullable
  @Override
  public String getServerAddress(HttpRequest request) {
    Uri.Host host = request.uri().authority().host();
    return host.isEmpty() ? null : host.address();
  }

  @Override
  public Integer getServerPort(HttpRequest request) {
    return request.uri().authority().port();
  }
}
