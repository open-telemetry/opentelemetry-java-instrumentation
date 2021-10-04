/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetAttributesOnEndExtractor;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.elasticsearch.client.Response;

final class ElasticsearchRestNetResponseAttributesExtractor
    extends NetAttributesOnEndExtractor<String, Response> {
  @Override
  public String transport(String operation, Response response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  public @Nullable String peerName(String operation, @Nullable Response response) {
    if (response != null) {
      return response.getHost().getHostName();
    }
    return null;
  }

  @Override
  public @Nullable Integer peerPort(String operation, @Nullable Response response) {
    if (response != null) {
      return response.getHost().getPort();
    }
    return null;
  }

  @Override
  public @Nullable String peerIp(String operation, @Nullable Response response) {
    if (response != null && response.getHost().getAddress() != null) {
      return response.getHost().getAddress().getHostAddress();
    }
    return null;
  }
}
