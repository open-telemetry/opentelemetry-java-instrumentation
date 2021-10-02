/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.v6_0;

import io.opentelemetry.instrumentation.api.instrumenter.net.InetSocketAddressNetAttributesExtractor;
import io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.ElasticTransportRequest;
import java.net.InetSocketAddress;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.elasticsearch.action.ActionResponse;

public class Elasticsearch6TransportNetAttributesExtractor
    extends InetSocketAddressNetAttributesExtractor<ElasticTransportRequest, ActionResponse> {
  @Override
  public @Nullable String transport(ElasticTransportRequest elasticTransportRequest) {
    return null;
  }

  @Override
  public @Nullable InetSocketAddress getAddress(
      ElasticTransportRequest elasticTransportRequest, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().address();
    }
    return null;
  }
}
