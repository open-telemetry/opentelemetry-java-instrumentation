/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.awssdk.v1_11;

import com.amazonaws.Request;
import com.amazonaws.Response;
import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;

public class AwsSdkNetAttributesExtractor extends NetAttributesExtractor<Request<?>, Response<?>> {
  @Override
  public @Nullable String transport(Request<?> request) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(Request<?> request, @Nullable Response<?> response) {
    return request.getEndpoint().getHost();
  }

  @Override
  public @Nullable Integer peerPort(Request<?> request, @Nullable Response<?> response) {
    return request.getEndpoint().getPort();
  }

  @Override
  public @Nullable String peerIp(Request<?> request, @Nullable Response<?> response) {
    return null;
  }
}
