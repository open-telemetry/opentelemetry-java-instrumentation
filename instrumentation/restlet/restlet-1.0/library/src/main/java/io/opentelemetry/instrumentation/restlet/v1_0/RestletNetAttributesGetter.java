/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_0;

import com.noelios.restlet.http.HttpCall;
import com.noelios.restlet.http.HttpRequest;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.restlet.data.Request;

final class RestletNetAttributesGetter implements NetServerAttributesGetter<Request> {

  @Override
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String hostName(Request request) {
    HttpCall call = httpCall(request);
    return call == null ? null : call.getHostDomain();
  }

  @Nullable
  @Override
  public Integer hostPort(Request request) {
    HttpCall call = httpCall(request);
    return call == null ? null : call.getServerPort();
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
    HttpCall call = httpCall(request);
    return call == null ? null : call.getServerAddress();
  }

  @Nullable
  private static HttpCall httpCall(Request request) {
    if (request instanceof HttpRequest) {
      return ((HttpRequest) request).getHttpCall();
    }
    return null;
  }
}
