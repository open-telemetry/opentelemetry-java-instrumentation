/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.osgi;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.apachehttpclient.v4_3.ApacheHttpClientTelemetry;
import org.apache.http.impl.client.CloseableHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.test.junit5.context.BundleContextExtension;

@ExtendWith(BundleContextExtension.class)
class ApacheHttpClientOsgiTest {

  @Test
  void telemetryWrapsClientInOsgi() throws Exception {
    ApacheHttpClientTelemetry telemetry = ApacheHttpClientTelemetry.create(OpenTelemetry.noop());
    try (CloseableHttpClient client = telemetry.createHttpClient()) {
      assertNotNull(client);
    }
  }
}
