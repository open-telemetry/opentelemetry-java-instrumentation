/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.akkahttp.client;

import akka.http.scaladsl.model.HttpRequest;
import akka.http.scaladsl.model.HttpResponse;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

class AkkaHttpNetAttributesGetter implements NetClientAttributesGetter<HttpRequest, HttpResponse> {

  @Override
  public String getTransport(HttpRequest httpRequest, @Nullable HttpResponse httpResponse) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public String getPeerName(HttpRequest httpRequest) {
    return httpRequest.uri().authority().host().address();
  }

  @Override
  public Integer getPeerPort(HttpRequest httpRequest) {
    return httpRequest.uri().authority().port();
  }
}
