/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.opensearch.rest;

import java.net.InetAddress;

public interface OpenSearchRestResponse {
  int getStatusCode();

  InetAddress getAddress();
}
