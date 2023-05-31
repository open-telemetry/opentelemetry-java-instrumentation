/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.server;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpUtil;
import javax.annotation.Nullable;

// TODO (trask) capture net attributes?
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
  public String getHostName(HttpRequest request) {
    return null;
  }

  @Nullable
  @Override
  public Integer getHostPort(HttpRequest request) {
    return null;
  }
}
