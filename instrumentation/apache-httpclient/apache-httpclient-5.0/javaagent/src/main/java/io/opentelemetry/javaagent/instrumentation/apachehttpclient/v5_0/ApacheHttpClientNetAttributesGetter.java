/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

final class ApacheHttpClientNetAttributesGetter
    implements NetClientAttributesGetter<ApacheHttpRequest, ApacheHttpResponse> {
  @Override
  public String transport(ApacheHttpRequest request, @Nullable ApacheHttpResponse response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(ApacheHttpRequest request) {
    return request.getPeerName();
  }

  @Override
  public Integer peerPort(ApacheHttpRequest request) {
    return request.getPeerPort();
  }
}
