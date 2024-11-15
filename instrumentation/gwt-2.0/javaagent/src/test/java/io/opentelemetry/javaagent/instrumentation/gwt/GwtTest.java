/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.gwt;

import static io.opentelemetry.instrumentation.testing.util.TelemetryDataUtil.orderByRootSpanName;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_METHOD;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SERVICE;
import static io.opentelemetry.semconv.incubating.RpcIncubatingAttributes.RPC_SYSTEM;
import static org.junit.jupiter.api.Assertions.assertEquals;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.instrumentation.test.utils.PortUtils;
import io.opentelemetry.instrumentation.testing.junit.AgentInstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.webapp.WebAppContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.BrowserWebDriverContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

class GwtTest {

  @RegisterExtension
  private static final InstrumentationExtension testing = AgentInstrumentationExtension.create();

  private static final Logger logger = LoggerFactory.getLogger(GwtTest.class);
  static int port;
  static Server server;
  static BrowserWebDriverContainer<?> browser;
  static URI address;

  static void startServer() throws Exception {
    port = PortUtils.findOpenPort();
    server = new Server(port);
    for (Connector connector : server.getConnectors()) {
      ((ServerConnector) connector).setHost("localhost");
    }

    WebAppContext webAppContext = new WebAppContext();
    webAppContext.setContextPath(getContextPath());
    webAppContext.setBaseResource(Resource.newResource(new File("build/testapp/web")));

    server.setHandler(webAppContext);
    server.start();
  }

  static void stopServer() throws Exception {
    server.stop();
    server.destroy();
  }

  @BeforeAll
  static void setup() throws Exception {
    startServer();

    Testcontainers.exposeHostPorts(port);

    browser =
        new BrowserWebDriverContainer<>()
            .withCapabilities(new ChromeOptions())
            .withLogConsumer(new Slf4jLogConsumer(logger));
    browser.start();

    address = new URI("http://host.testcontainers.internal:" + port + getContextPath() + "/");
  }

  @AfterAll
  static void cleanup() throws Exception {
    stopServer();
    browser.stop();
  }

  static final String getContextPath() {
    return "/xyz";
  }

  RemoteWebDriver getDriver() {
    RemoteWebDriver driver =
        new RemoteWebDriver(browser.getSeleniumAddress(), new ChromeOptions(), false);
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(30));
    return driver;
  }

  @Test
  void testGwt() {
    RemoteWebDriver driver = getDriver();

    // fetch the test page
    driver.get(address.resolve("greeting.html").toString());

    driver.findElement(By.className("greeting.button"));
    testing.waitAndAssertSortedTraces(
        orderByRootSpanName("GET " + getContextPath() + "/*", "GET"),
        trace -> {
          // /xyz/greeting.html
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("GET " + getContextPath() + "/*")
                      .hasKind(SpanKind.SERVER)
                      .hasNoParent());
        },
        trace -> {
          // /xyz/greeting/greeting.nocache.js
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("GET " + getContextPath() + "/*")
                      .hasKind(SpanKind.SERVER)
                      .hasNoParent());
        },
        trace -> {
          // /xyz/greeting/1B105441581A8F41E49D5DF3FB5B55BA.cache.html
          trace.hasSpansSatisfyingExactly(
              span ->
                  span.hasName("GET " + getContextPath() + "/*")
                      .hasKind(SpanKind.SERVER)
                      .hasNoParent());
        },
        trace -> {
          // /favicon.ico
          trace.hasSpansSatisfyingExactly(
              span -> span.hasName("GET").hasKind(SpanKind.SERVER).hasNoParent());
        });

    testing.clearData();

    // click a button to trigger calling java code
    driver.findElement(By.className("greeting.button")).click();
    assertEquals(driver.findElement(By.className("message.received")).getText(), "Hello, Otel");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("POST " + getContextPath() + "/greeting/greet")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent(),
                span ->
                    span.hasName("test.gwt.shared.MessageService/sendMessage")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM, "gwt"),
                            equalTo(RPC_SERVICE, "test.gwt.shared.MessageService"),
                            equalTo(RPC_METHOD, "sendMessage"))));

    testing.clearData();

    // click a button to trigger calling java code
    driver.findElement(By.className("error.button")).click();
    assertEquals(driver.findElement(By.className("error.received")).getText(), "Error");

    testing.waitAndAssertTraces(
        trace ->
            trace.hasSpansSatisfyingExactly(
                span ->
                    span.hasName("POST " + getContextPath() + "/greeting/greet")
                        .hasKind(SpanKind.SERVER)
                        .hasNoParent(),
                span ->
                    span.hasName("test.gwt.shared.MessageService/sendMessage")
                        .hasKind(SpanKind.SERVER)
                        .hasParent(trace.getSpan(0))
                        .hasException(new IOException())
                        .hasAttributesSatisfyingExactly(
                            equalTo(RPC_SYSTEM, "gwt"),
                            equalTo(RPC_SERVICE, "test.gwt.shared.MessageService"),
                            equalTo(RPC_METHOD, "sendMessage"))));

    driver.close();
  }
}
