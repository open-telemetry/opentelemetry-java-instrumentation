/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetAttributesOnEndExtractor;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportRequest;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.elasticsearch.action.ActionResponse;

public class Elasticsearch6TransportNetAttributesExtractor
    extends InetSocketAddressNetAttributesOnEndExtractor<ElasticTransportRequest, ActionResponse> {
  @Override
  public @Nullable String transport(
      ElasticTransportRequest request, @Nullable ActionResponse response) {
    return null;
  }

  @Override
  public @Nullable InetSocketAddress getAddress(
      ElasticTransportRequest request, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().address();
    }
    return null;
  }
}
