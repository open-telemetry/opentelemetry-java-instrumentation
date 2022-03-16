/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.rest;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import io.opentelemetry.semconv.trace.attributes.SemanticAttributes;
import javax.annotation.Nullable;
import org.elasticsearch.client.Response;

final class ElasticsearchRestNetResponseAttributesGetter
    implements NetClientAttributesGetter<ElasticsearchRestRequest, Response> {

  @Override
  public String transport(ElasticsearchRestRequest request, Response response) {
    return SemanticAttributes.NetTransportValues.IP_TCP;
  }

  @Override
  @Nullable
  public String peerName(ElasticsearchRestRequest request, @Nullable Response response) {
    if (response != null) {
      return response.getHost().getHostName();
    }
    return null;
  }

  @Override
  @Nullable
  public Integer peerPort(ElasticsearchRestRequest request, @Nullable Response response) {
    if (response != null) {
      return response.getHost().getPort();
    }
    return null;
  }

  @Override
  @Nullable
  public String peerIp(ElasticsearchRestRequest request, @Nullable Response response) {
    if (response != null && response.getHost().getAddress() != null) {
      return response.getHost().getAddress().getHostAddress();
    }
    return null;
  }
}
