/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.java.v3_0;

import java.net.InetAddress;

public interface OpenSearchJavaResponse {
  int getStatusCode();

  InetAddress getAddress();
}
