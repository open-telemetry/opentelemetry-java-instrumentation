/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticsearch.transport.common.v5_0;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class ElasticTransportRequest {

  public static ElasticTransportRequest create(
      Object action, Object request, @Nullable String serverAddress, @Nullable Integer serverPort) {
    return new AutoValue_ElasticTransportRequest(action, request, serverAddress, serverPort);
  }

  public abstract Object getAction();

  public abstract Object getRequest();

  /**
   * The address of the target the client was configured with, or {@code null} for a client that
   * talks to a node in the same process.
   */
  @Nullable
  public abstract String getServerAddress();

  /** The port of a single configured address, {@code null} for a target that names several. */
  @Nullable
  public abstract Integer getServerPort();
}
