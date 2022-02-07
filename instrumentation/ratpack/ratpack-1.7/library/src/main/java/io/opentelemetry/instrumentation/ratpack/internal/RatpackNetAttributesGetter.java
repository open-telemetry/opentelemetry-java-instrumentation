/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import ratpack.http.Request;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class RatpackNetAttributesGetter implements NetServerAttributesGetter<Request> {
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
