/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.testing.junit.http;

import io.opentelemetry.instrumentation.testing.InstrumentationTestRunner;
import io.opentelemetry.testing.internal.armeria.client.WebClient;
import java.net.URI;
import java.net.URISyntaxException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class AbstractHttpServerUsingTest<SERVER> {
  private static final Logger logger = LoggerFactory.getLogger(AbstractHttpServerUsingTest.class);

  public static final String TEST_CLIENT_IP = "1.1.1.1";
  public static final String TEST_USER_AGENT = "test-user-agent";

  InstrumentationTestRunner testing;
  private SERVER server;
  public WebClient client;
  public int port;
  public URI address;

  protected abstract SERVER setupServer() throws Exception;

  protected abstract void stopServer(SERVER server) throws Exception;

  protected final InstrumentationTestRunner testing() {
    return testing;
  }

  protected void startServer() {
    if (address == null) {
      address = buildAddress();
    }

    try {
      server = setupServer();
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to start server", exception);
    }
    if (server != null) {
      logger.info(
          getClass().getName()
              + " http server started at: http://localhost:"
              + port
              + getContextPath());
    }
  }

  protected abstract String getContextPath();

  protected void cleanupServer() {
    if (server == null) {
      logger.info(getClass().getName() + " can't stop null server");
      return;
    }
    try {
      stopServer(server);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to stop server", exception);
    }
    server = null;
    logger.info(getClass().getName() + " http server stopped at: http://localhost:" + port + "/");
  }

  protected URI buildAddress() {
    try {
      return new URI("http://localhost:" + port + getContextPath() + "/");
    } catch (URISyntaxException exception) {
      throw new IllegalStateException(exception);
    }
  }

  @BeforeEach
  void verifyExtension() {
    if (testing == null) {
      throw new AssertionError(
          "Subclasses of AbstractHttpServerUsingTest must register HttpServerInstrumentationExtension");
    }
  }

  protected String resolveAddress(ServerEndpoint uri) {
    return resolveAddress(uri, "h1c://");
  }

  protected String resolveAddress(ServerEndpoint uri, String protocolPrefix) {
    String url = uri.resolvePath(address).toString();
    // Force HTTP/1 via h1c so upgrade requests don't show up as traces
    url = url.replace("http://", protocolPrefix);
    if (uri.getQuery() != null) {
      url += "?" + uri.getQuery();
    }
    return url;
  }

  final void setTesting(InstrumentationTestRunner testing, WebClient client, int port) {
    setTesting(testing, client, port, null);
  }

  final void setTesting(
      InstrumentationTestRunner testing, WebClient client, int port, URI address) {
    this.testing = testing;
    this.client = client;
    this.port = port;
    this.address = address;
  }
}
