/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common.v1_0;

import java.net.InetAddress;
import javax.annotation.Nullable;

public interface OpenSearchRestResponse {
  int getStatusCode();

  @Nullable
  InetAddress getAddress();
}
