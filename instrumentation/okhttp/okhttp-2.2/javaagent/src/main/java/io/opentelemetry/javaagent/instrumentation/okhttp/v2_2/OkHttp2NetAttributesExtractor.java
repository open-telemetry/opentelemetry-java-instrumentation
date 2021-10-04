/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.okhttp.v2_2;

import com.squareup.okhttp.Request;
import com.squareup.okhttp.Response;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class OkHttp2NetAttributesExtractor extends NetAttributesExtractor<Request, Response> {
  public OkHttp2NetAttributesExtractor() {
    super(NetPeerAttributeExtraction.ON_START);
  }

  @Override
  public String transport(Request request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(Request request, @Nullable Response response) {
    return request.url().getHost();
  }

  @Override
  public Integer peerPort(Request request, @Nullable Response response) {
    return request.url().getPort();
  }

  @Override
  public @Nullable String peerIp(Request request, @Nullable Response response) {
    return null;
  }
}
