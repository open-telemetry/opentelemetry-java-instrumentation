/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin.v14_2;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.equalTo;
import static io.opentelemetry.semconv.CodeAttributes.CODE_FUNCTION_NAME;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.Testcontainers;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.selenium.BrowserWebDriverContainer;

class VaadinLatestTest extends AbstractVaadinTest {

  private static final Logger logger = LoggerFactory.getLogger(VaadinLatestTest.class);

  private BrowserWebDriverContainer browser;

  @BeforeAll
  protected void setupBrowserContainer() throws URISyntaxException {
    Testcontainers.exposeHostPorts(port);

    browser =
        new BrowserWebDriverContainer("selenium/standalone-chrome:4.43.0")
            .withLogConsumer(new Slf4jLogConsumer(logger));
    browser.start();
    cleanup.deferAfterAll(browser::stop);

    address = new URI("http://host.testcontainers.internal:" + port + getContextPath() + "/");
  }

  @Override
  protected boolean isProductionMode() {
    return false;
  }

  private void waitForStart(RemoteWebDriver driver) {
    driver.manage().timeouts().implicitlyWait(Duration.ofMinutes(3));
    driver.get(address.resolve("main").toString());
    driver.findElement(By.id("main.label"));
    testing.clearData();
    driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(30));
  }

  private RemoteWebDriver getWebDriver() {
    return new RemoteWebDriver(browser.getSeleniumAddress(), new ChromeOptions(), false);
  }

  private void assertButtonClick() {
    await()
        .untilAsserted(
            () -> {
              List<List<SpanData>> traces = testing.waitForTraces(1);
              List<SpanData> spans = traces.get(0);
              assertThat(spans).anySatisfy(span -> assertThat(span).hasKind(SpanKind.SERVER));
              if (controllerTelemetryEnabled()) {
                assertThat(spans)
                    .satisfies(
                        actualSpans -> {
                          assertThat(actualSpans.get(0))
                              .hasName("POST " + getContextPath() + "/main")
                              .hasNoParent()
                              .hasKind(SpanKind.SERVER);
                          assertThat(actualSpans.get(1))
                              .hasName("SpringVaadinServletService.handleRequest")
                              .hasParent(actualSpans.get(0))
                              .hasKind(SpanKind.INTERNAL);
                          assertThat(actualSpans.get(actualSpans.size() - 2))
                              .hasName("UidlRequestHandler.handleRequest")
                              .hasParent(actualSpans.get(1))
                              .hasKind(SpanKind.INTERNAL);
                          assertThat(actualSpans.get(actualSpans.size() - 1))
                              .hasName("EventRpcHandler.handle/click")
                              .hasParent(actualSpans.get(actualSpans.size() - 2))
                              .hasKind(SpanKind.INTERNAL)
                              .hasAttributesSatisfyingExactly(
                                  equalTo(
                                      CODE_FUNCTION_NAME,
                                      "com.vaadin.flow.server.communication.rpc.EventRpcHandler.handle"));
                        });
              } else {
                assertNoControllerSpans(spans);
              }
            });
  }

  @Test
  void navigateFromMainToOtherView() {
    RemoteWebDriver driver = getWebDriver();
    cleanup.deferCleanup(driver::quit);

    waitForStart(driver);
    driver.get(address.resolve("main").toString());
    assertThat(driver.findElement(By.id("main.label")).getText()).isEqualTo("Main view");
    assertFirstRequest();

    testing.clearData();
    driver.findElement(By.id("main.button")).click();
    assertThat(driver.findElement(By.id("other.label")).getText()).isEqualTo("Other view");
    assertButtonClick();
  }

  @Override
  void assertFirstRequest() {
    await()
        .untilAsserted(
            () -> {
              List<List<SpanData>> traces = testing.waitForTraces(1);
              if (!controllerTelemetryEnabled()) {
                for (List<SpanData> trace : traces) {
                  assertThat(trace).anySatisfy(span -> assertThat(span).hasKind(SpanKind.SERVER));
                  assertNoControllerSpans(trace);
                }
                return;
              }
              TracesAssert.assertThat(traces)
                  .satisfies(
                      trace -> {
                        assertThat(trace.get(0))
                            .satisfies(
                                spans ->
                                    assertThat(spans.get(0))
                                        .hasName("GET IndexHtmlRequestHandler.handleRequest")
                                        .hasNoParent()
                                        .hasKind(SpanKind.SERVER));
                        assertThat(trace)
                            .anySatisfy(
                                spans -> {
                                  assertThat(spans.get(0))
                                      .hasName("POST " + getContextPath())
                                      .hasNoParent()
                                      .hasKind(SpanKind.SERVER);
                                  assertThat(spans.get(1))
                                      .hasName("SpringVaadinServletService.handleRequest")
                                      .hasParent(spans.get(0))
                                      .hasKind(SpanKind.INTERNAL);
                                  // we don't assert all the handler spans as these vary between
                                  // vaadin versions
                                  assertThat(spans.get(spans.size() - 3))
                                      .hasName("UidlRequestHandler.handleRequest")
                                      .hasParent(spans.get(1))
                                      .hasKind(SpanKind.INTERNAL);
                                  assertThat(spans.get(spans.size() - 2))
                                      .hasName("PublishedServerEventHandlerRpcHandler.handle")
                                      .hasParent(spans.get(spans.size() - 3))
                                      .hasKind(SpanKind.INTERNAL);
                                  assertThat(spans.get(spans.size() - 1))
                                      .hasName("UI.connectClient")
                                      .hasParent(spans.get(spans.size() - 2))
                                      .hasKind(SpanKind.INTERNAL);
                                });
                      });
            });
  }
}
