/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;

class AwsSdkNetAttributesGetter implements NetClientAttributesGetter<Request<?>, Response<?>> {

  @Override
  public String getTransport(Request<?> request, @Nullable Response<?> response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String getPeerName(Request<?> request) {
    return request.getEndpoint().getHost();
  }

  @Override
  public Integer getPeerPort(Request<?> request) {
    return request.getEndpoint().getPort();
  }
}
