/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

public final class OkHttp2NetAttributesGetter
    implements NetClientAttributesGetter<Request, Response> {

  @Override
  public String transport(Request request, @Nullable Response response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(Request request, @Nullable Response response) {
    return request.url().getHost();
  }

  @Override
  public Integer peerPort(Request request, @Nullable Response response) {
    return request.url().getPort();
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
