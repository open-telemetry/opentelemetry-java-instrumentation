/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vertx.v3_9.aerospike;

import io.opentelemetry.instrumentation.api.semconv.network.NetworkAttributesGetter;
import io.opentelemetry.instrumentation.api.semconv.network.ServerAttributesGetter;
import javax.annotation.Nullable;

enum AerospikeNetAttributesGetter
    implements
        ServerAttributesGetter<AerospikeRequest>,
        NetworkAttributesGetter<AerospikeRequest, Void> {
  INSTANCE;

  @Nullable
  @Override
  public String getServerAddress(AerospikeRequest request) {
    return request.getHost();
  }

  @Nullable
  @Override
  public Integer getServerPort(AerospikeRequest request) {
    return request.getPort();
  }

  @Override
  @Nullable
  public String getNetworkPeerAddress(AerospikeRequest request, @Nullable Void unused) {
    return request.getPeerAddress();
  }

  @Override
  @Nullable
  public Integer getNetworkPeerPort(AerospikeRequest request, @Nullable Void unused) {
    return request.getPeerPort();
  }
}

