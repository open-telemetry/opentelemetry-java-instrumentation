/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.restlet.data.Request;

final class RestletNetAttributesGetter implements NetServerAttributesGetter<Request> {
  @Override
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
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
