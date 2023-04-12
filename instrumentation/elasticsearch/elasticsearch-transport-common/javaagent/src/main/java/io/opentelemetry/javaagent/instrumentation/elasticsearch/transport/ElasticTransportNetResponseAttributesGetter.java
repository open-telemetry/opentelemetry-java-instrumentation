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
  public String getPeerName(ElasticTransportRequest request) {
    return null;
  }

  @Override
  @Nullable
  public Integer getPeerPort(ElasticTransportRequest request) {
    return null;
  }

  @Override
  @Nullable
  public String getSockPeerAddr(
      ElasticTransportRequest request, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().getAddress();
    }
    return null;
  }

  @Nullable
  @Override
  public String getSockPeerName(
      ElasticTransportRequest request, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().getHost();
    }
    return null;
  }

  @Nullable
  @Override
  public Integer getSockPeerPort(
      ElasticTransportRequest request, @Nullable ActionResponse response) {
    if (response != null && response.remoteAddress() != null) {
      return response.remoteAddress().getPort();
    }
    return null;
  }
}
