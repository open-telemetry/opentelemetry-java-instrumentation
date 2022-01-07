/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesAdapter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import okhttp3.Request;
import okhttp3.Response;

public final class OkHttpNetAttributesAdapter
    implements NetClientAttributesAdapter<Request, Response> {

  @Override
  public String transport(Request request, @Nullable Response response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(Request request, @Nullable Response response) {
    return request.url().host();
  }

  @Override
  public Integer peerPort(Request request, @Nullable Response response) {
    return request.url().port();
  }

  @Override
  @Nullable
  public String peerIp(Request request, @Nullable Response response) {
    return null;
  }
}
