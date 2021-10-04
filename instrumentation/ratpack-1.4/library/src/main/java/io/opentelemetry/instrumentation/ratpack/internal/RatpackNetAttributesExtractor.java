/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesOnStartExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;
import ratpack.http.Request;
import ratpack.http.Response;

public final class RatpackNetAttributesExtractor
    extends NetAttributesOnStartExtractor<Request, Response> {
  @Override
  @Nullable
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(Request request) {
    return null;
  }

  @Override
  public Integer peerPort(Request request) {
    return request.getRemoteAddress().getPort();
  }

  @Override
  @Nullable
  public String peerIp(Request request) {
    return null;
  }
}
