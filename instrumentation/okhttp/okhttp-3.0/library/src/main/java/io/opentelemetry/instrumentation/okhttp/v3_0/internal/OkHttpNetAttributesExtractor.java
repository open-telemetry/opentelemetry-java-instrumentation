/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import okhttp3.Request;
import okhttp3.Response;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class OkHttpNetAttributesExtractor extends NetAttributesExtractor<Request, Response> {
  public OkHttpNetAttributesExtractor() {
    super(NetPeerAttributeExtraction.ON_START);
  }

  @Override
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(Request request, @Nullable Response response) {
    return request.url().host();
  }

  @Override
  public Integer peerPort(Request request, @Nullable Response response) {
    return request.url().port();
  }

  @Override
  public @Nullable String peerIp(Request request, @Nullable Response response) {
    return null;
  }
}
