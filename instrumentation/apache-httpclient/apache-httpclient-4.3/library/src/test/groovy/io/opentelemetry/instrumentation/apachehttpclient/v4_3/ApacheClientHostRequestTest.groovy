/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.apachehttpclient.v4_3

import io.opentelemetry.instrumentation.test.LibraryTestTrait
import org.apache.http.impl.client.CloseableHttpClient

class ApacheClientHostRequestTest extends AbstractApacheClientHostRequestTest implements LibraryTestTrait {
  @Override
  protected CloseableHttpClient createClient() {
    return ApacheHttpClientTracing.create(openTelemetry).newHttpClient()
  }

  // library instrumentation doesn't have a good way of suppressing nested CLIENT spans yet
  @Override
  boolean testWithClientParent() {
    false
  }
}
