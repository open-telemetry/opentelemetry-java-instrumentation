/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v2_0.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.restlet.Request;
import org.restlet.engine.http.HttpRequest;
import org.restlet.engine.http.ServerCall;

final class RestletNetAttributesGetter implements NetServerAttributesGetter<Request> {

  @Override
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String hostName(Request request) {
    ServerCall call = serverCall(request);
    return call == null ? null : call.getHostDomain();
  }

  @Nullable
  @Override
  public Integer hostPort(Request request) {
    ServerCall call = serverCall(request);
    return call == null ? null : call.getServerPort();
  }

  @Nullable
  @Override
  public String sockFamily(Request request) {
    return null;
  }

  @Override
  @Nullable
  public String sockPeerAddr(Request request) {
    return request.getClientInfo().getAddress();
  }

  @Override
  public Integer sockPeerPort(Request request) {
    return request.getClientInfo().getPort();
  }

  @Nullable
  @Override
  public String sockHostAddr(Request request) {
    ServerCall call = serverCall(request);
    return call == null ? null : call.getServerAddress();
  }

  @Nullable
  @Override
  public Integer sockHostPort(Request request) {
    return null;
  }

  @Nullable
  private static ServerCall serverCall(Request request) {
    if (request instanceof HttpRequest) {
      return ((HttpRequest) request).getHttpCall();
    }
    return null;
  }
}
