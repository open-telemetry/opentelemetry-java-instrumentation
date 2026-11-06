/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import java.util.Map;

/**
 * Helper class for http client tests which require a single connection.
 *
 * <p>Tests for specific library should provide an implementation which satisfies the following
 * conditions:
 *
 * <ul>
 *   <li>Has a constructor which accepts target host and port
 *   <li>For a given instance all invocations of {@link #doRequest(String, Map)} will reuse the same
 *       underlying connection to target host.
 * </ul>
 */
public interface SingleConnection {
  String REQUEST_ID_HEADER = "test-request-id";

  int doRequest(String path, Map<String, String> headers) throws Exception;
}
