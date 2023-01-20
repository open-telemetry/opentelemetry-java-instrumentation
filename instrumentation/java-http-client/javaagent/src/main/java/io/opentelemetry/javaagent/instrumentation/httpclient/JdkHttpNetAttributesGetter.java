/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.httpclient;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import javax.annotation.Nullable;

public class JdkHttpNetAttributesGetter
    implements NetClientAttributesGetter<HttpRequest, HttpResponse<?>> {

  @Override
  public String transport(HttpRequest httpRequest, @Nullable HttpResponse<?> response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(HttpRequest httpRequest) {
    return httpRequest.uri().getHost();
  }

  @Override
  @Nullable
  public Integer peerPort(HttpRequest httpRequest) {
    return httpRequest.uri().getPort();
  }
}
