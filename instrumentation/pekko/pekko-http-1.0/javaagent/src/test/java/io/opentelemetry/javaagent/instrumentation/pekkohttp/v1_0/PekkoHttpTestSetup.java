/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.pekkohttp.v1_0;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest.TEST_CLIENT_IP;
import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpServerUsingTest.TEST_USER_AGENT;

import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.testing.internal.armeria.client.ClientFactory;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import io.opentelemetry.testing.internal.armeria.client.logging.LoggingClient;
import io.opentelemetry.testing.internal.armeria.common.HttpHeaderNames;
import java.time.Duration;

public final class PekkoHttpTestSetup {

  private final int port;
  private final WebClient client;

  public PekkoHttpTestSetup() {
    port = PortUtils.findOpenPort();
    client =
        WebClient.builder()
            .responseTimeout(Duration.ofMinutes(1))
            .writeTimeout(Duration.ofMinutes(1))
            .factory(ClientFactory.builder().connectTimeout(Duration.ofMinutes(1)).build())
            .setHeader(HttpHeaderNames.USER_AGENT, TEST_USER_AGENT)
            .setHeader(HttpHeaderNames.X_FORWARDED_FOR, TEST_CLIENT_IP)
            .decorator(LoggingClient.newDecorator())
            .build();
  }

  public int getPort() {
    return port;
  }

  public WebClient getClient() {
    return client;
  }
}
