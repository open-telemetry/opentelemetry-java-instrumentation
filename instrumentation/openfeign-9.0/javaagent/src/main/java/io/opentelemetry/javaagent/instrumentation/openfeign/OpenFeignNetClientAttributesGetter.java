/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.openfeign;

import feign.Response;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import java.net.URI;
import javax.annotation.Nullable;

enum OpenFeignNetClientAttributesGetter
    implements NetClientAttributesGetter<ExecuteAndDecodeRequest, Response> {
  INSTANCE;

  @Override
  public String transport(ExecuteAndDecodeRequest request, @Nullable Response response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public String peerName(ExecuteAndDecodeRequest request) {
    String host = request.getTemplateUri().getHost();
    if (host == null || "".equals(host)) {
      return request.getTarget().name();
    }
    return host;
  }

  @Override
  public Integer peerPort(ExecuteAndDecodeRequest request) {
    URI uri = request.getTemplateUri();
    return uri.getPort();
  }
}
