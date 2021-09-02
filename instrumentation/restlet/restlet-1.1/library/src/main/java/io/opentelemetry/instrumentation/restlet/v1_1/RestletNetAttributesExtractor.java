/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.restlet.data.Request;
import org.restlet.data.Response;

final class RestletNetAttributesExtractor extends NetAttributesExtractor<Request, Response> {
  @Override
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(Request request, @Nullable Response response) {
    return request.getHostRef().getUserInfo();
  }

  @Override
  public @Nullable Integer peerPort(Request request, @Nullable Response response) {
    return request.getClientInfo().getPort();
  }

  @Override
  public @Nullable String peerIp(Request request, @Nullable Response response) {
    return request.getClientInfo().getAddress();
  }
}
