/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesServerExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.restlet.data.Request;
import org.restlet.data.Response;

final class RestletNetAttributesExtractor extends NetAttributesServerExtractor<Request, Response> {
  @Override
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(Request request) {
    return null;
  }

  @Override
  public Integer peerPort(Request request) {
    return request.getClientInfo().getPort();
  }

  @Override
  public @Nullable String peerIp(Request request) {
    return request.getClientInfo().getAddress();
  }
}
