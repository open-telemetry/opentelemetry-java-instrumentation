/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.restlet.Request;
import org.restlet.Response;

final class RestletNetAttributesExtractor extends NetServerAttributesExtractor<Request, Response> {
  @Override
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
    return request.getClientInfo().getPort();
  }

  @Override
  @Nullable
  public String peerIp(Request request) {
    return request.getClientInfo().getAddress();
  }
}
