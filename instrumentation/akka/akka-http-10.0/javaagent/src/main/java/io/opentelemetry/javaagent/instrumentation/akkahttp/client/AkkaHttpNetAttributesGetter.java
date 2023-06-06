/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.javaagent.instrumentation.akkahttp.AkkaHttpUtil;
import javax.annotation.Nullable;

class AkkaHttpNetAttributesGetter implements NetClientAttributesGetter<HttpRequest, HttpResponse> {

  @Nullable
  @Override
  public String getNetworkProtocolName(
      HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return AkkaHttpUtil.protocolName(httpRequest);
  }

  @Nullable
  @Override
  public String getNetworkProtocolVersion(
      HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return AkkaHttpUtil.protocolVersion(httpRequest);
  }

  @Override
  public String getServerAddress(HttpRequest httpRequest) {
    return httpRequest.uri().authority().host().address();
  }

  @Override
  public Integer getServerPort(HttpRequest httpRequest) {
    return httpRequest.uri().authority().port();
  }
}
