/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest.common;

import java.net.InetAddress;
import javax.annotation.Nullable;

public interface OpenSearchRestResponse {
  int getStatusCode();

  @Nullable
  InetAddress getAddress();
}
