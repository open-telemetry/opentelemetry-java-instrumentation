/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ratpack.client;

import io.opentelemetry.instrumentation.testing.junit.http.Options;
import ratpack.http.client.HttpClient;

public abstract class AbstractRatpackPooledHttpClientTest extends AbstractRatpackHttpClientTest {

  @Override
  protected HttpClient buildHttpClient() throws Exception {
    return buildHttpClient(spec -> spec.poolSize(5));
  }

  @Override
  protected void configure(Options.Builder optionsBuilder) {
    super.configure(optionsBuilder);

    // this test is already run for RatpackHttpClientTest
    // returning null here to avoid running the same test twice
    optionsBuilder.setSingleConnectionFactory((host, port) -> null);
  }
}
