/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.v3_0;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

@AutoValue
public abstract class OpenSearchRequest {

  public static OpenSearchRequest create(
      String method,
      String endpoint,
      @Nullable String body,
      @Nullable String serverAddress,
      @Nullable Integer serverPort) {
    return new AutoValue_OpenSearchRequest(method, endpoint, body, serverAddress, serverPort);
  }

  public abstract String getMethod();

  public abstract String getEndpoint();

  @Nullable
  public abstract String getBody();

  @Nullable
  public abstract String getServerAddress();

  @Nullable
  public abstract Integer getServerPort();
}
