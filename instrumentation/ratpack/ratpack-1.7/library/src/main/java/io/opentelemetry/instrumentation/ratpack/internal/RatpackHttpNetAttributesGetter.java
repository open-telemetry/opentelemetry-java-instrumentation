/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import ratpack.http.client.HttpResponse;
import ratpack.http.client.RequestSpec;

public final class RatpackHttpNetAttributesGetter
    implements NetClientAttributesGetter<RequestSpec, HttpResponse> {
  @Override
  public String transport(RequestSpec request, @Nullable HttpResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(RequestSpec request, @Nullable HttpResponse response) {
    return request.getUri().getHost();
  }

  @Override
  public Integer peerPort(RequestSpec request, @Nullable HttpResponse response) {
    return request.getUri().getPort();
  }

  @Override
  @Nullable
  public String peerIp(RequestSpec request, @Nullable HttpResponse response) {
    return null;
  }
}
