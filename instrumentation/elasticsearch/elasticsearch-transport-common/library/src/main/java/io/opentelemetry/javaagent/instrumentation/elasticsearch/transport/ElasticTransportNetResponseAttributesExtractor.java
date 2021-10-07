/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesExtractor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.elasticsearch.action.ActionResponse;

public class ElasticTransportNetResponseAttributesExtractor
    extends NetClientAttributesExtractor<ElasticTransportRequest, ActionResponse> {
  @Override
  public @Nullable String transport(
      ElasticTransportRequest request, @Nullable ActionResponse response) {
    return null;
  }

  @Override
  public @Nullable String peerName(
      ElasticTransportRequest request, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().getHost();
    }
    return null;
  }

  @Override
  public @Nullable Integer peerPort(
      ElasticTransportRequest request, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().getPort();
    }
    return null;
  }

  @Override
  public @Nullable String peerIp(
      ElasticTransportRequest request, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().getAddress();
    }
    return null;
  }
}
