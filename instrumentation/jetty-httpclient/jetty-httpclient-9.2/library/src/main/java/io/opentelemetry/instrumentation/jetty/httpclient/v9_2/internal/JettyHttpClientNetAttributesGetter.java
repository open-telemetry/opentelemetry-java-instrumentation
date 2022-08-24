/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jetty.httpclient.v9_2.internal;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.api.Response;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public class JettyHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<Request, Response> {

  @Override
  public String transport(Request request, @Nullable Response response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(Request request, @Nullable Response response) {
    return request.getHost();
  }

  @Override
  @Nullable
  public Integer peerPort(Request request, @Nullable Response response) {
    return request.getPort();
  }

  @Nullable
  @Override
  public String sockFamily(Request request, @Nullable Response response) {
    return null;
  }

  @Nullable
  @Override
  public String sockPeerAddr(Request request, @Nullable Response response) {
    return null;
  }

  @Nullable
  @Override
  public String sockPeerName(Request request, @Nullable Response response) {
    return null;
  }

  @Nullable
  @Override
  public Integer sockPeerPort(Request request, @Nullable Response response) {
    return null;
  }
}
