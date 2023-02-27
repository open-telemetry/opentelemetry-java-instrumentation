/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.restlet.v1_1;

import com.noelios.restlet.http.HttpCall;
import com.noelios.restlet.http.HttpRequest;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetServerAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.restlet.data.Request;

final class RestletNetAttributesGetter implements NetServerAttributesGetter<Request> {

  @Override
  public String getTransport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Nullable
  @Override
  public String getHostName(Request request) {
    HttpCall call = httpCall(request);
    return call == null ? null : call.getHostDomain();
  }

  @Nullable
  @Override
  public Integer getHostPort(Request request) {
    HttpCall call = httpCall(request);
    return call == null ? null : call.getServerPort();
  }

  @Override
  @Nullable
  public String getSockPeerAddr(Request request) {
    return request.getClientInfo().getAddress();
  }

  @Override
  public Integer getSockPeerPort(Request request) {
    return request.getClientInfo().getPort();
  }

  @Nullable
  @Override
  public String getSockHostAddr(Request request) {
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
