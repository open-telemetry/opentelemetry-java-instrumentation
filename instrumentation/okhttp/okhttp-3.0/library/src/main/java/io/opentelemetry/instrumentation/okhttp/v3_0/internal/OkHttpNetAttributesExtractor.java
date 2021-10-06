/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.okhttp.v3_0.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import okhttp3.Request;
import okhttp3.Response;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class OkHttpNetAttributesExtractor
    extends NetServerAttributesExtractor<Request, Response> {
  @Override
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(Request request) {
    return request.url().host();
  }

  @Override
  public Integer peerPort(Request request) {
    return request.url().port();
  }

  @Override
  public @Nullable String peerIp(Request request) {
    return null;
  }
}
