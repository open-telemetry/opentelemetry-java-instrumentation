/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport;

import io.opentelemetry.instrumentation.api.instrumenter.net.NetClientAttributesGetter;
import javax.annotation.Nullable;
import org.elasticsearch.action.ActionResponse;

public class ElasticTransportNetResponseAttributesGetter
    implements NetClientAttributesGetter<ElasticTransportRequest, ActionResponse> {

  @Override
  @Nullable
  public String transport(ElasticTransportRequest request, @Nullable ActionResponse response) {
    return null;
  }

  @Override
  @Nullable
  public String peerName(ElasticTransportRequest request, @Nullable ActionResponse response) {
    return null;
  }

  @Override
  @Nullable
  public Integer peerPort(ElasticTransportRequest request, @Nullable ActionResponse response) {
    return null;
  }

  @Nullable
  @Override
  public String sockFamily(ElasticTransportRequest request, @Nullable ActionResponse response) {
    return null;
  }

  @Override
  @Nullable
  public String sockPeerAddr(ElasticTransportRequest request, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().getAddress();
    }
    return null;
  }

  @Nullable
  @Override
  public String sockPeerName(ElasticTransportRequest request, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().getHost();
    }
    return null;
  }

  @Nullable
  @Override
  public Integer sockPeerPort(ElasticTransportRequest request, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().getPort();
    }
    return null;
  }
}
