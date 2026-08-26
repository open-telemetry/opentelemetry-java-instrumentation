/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import com.google.auto.value.AutoValue;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.SearchPeerState;
import javax.annotation.Nullable;

@AutoValue
public abstract class OpenSearchRestRequest {

  private final SearchPeerState peerState = new SearchPeerState();

  public static OpenSearchRestRequest create(
      String method, String endpoint, @Nullable OpenSearchServerTarget serverTarget) {
    return new AutoValue_OpenSearchRestRequest(method, endpoint, serverTarget);
  }

  public abstract String getMethod();

  public abstract String getEndpoint();

  @Nullable
  public abstract OpenSearchServerTarget getServerTarget();

  public final SearchPeerState getPeerState() {
    return peerState;
  }
}
