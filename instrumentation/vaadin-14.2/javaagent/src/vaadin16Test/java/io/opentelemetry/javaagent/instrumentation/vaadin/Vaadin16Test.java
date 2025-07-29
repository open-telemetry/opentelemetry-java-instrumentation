/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.TracesAssert;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class Vaadin16Test extends AbstractVaadinTest {

  @Override
  protected void prepareVaadinBaseDir(File baseDir) {
    Path basePath = Paths.get(baseDir.toURI());
    // underscore "_" prefix is used to hide these files from renovate
    // (they are only used for tests ok for them to have outdated dependencies)
    copyClasspathResource("/pnpm/_package.json", basePath.resolve("package.json"));
    copyClasspathResource("/pnpm/_pnpm-lock.yaml", basePath.resolve("pnpm-lock.yaml"));
  }

  @Override
  void assertFirstRequest() {
    await()
        .untilAsserted(
            () -> {
              List<List<SpanData>> traces = testing.waitForTraces(1);
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
                                      .hasName("POST " + getContextPath() + "/main")
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
                                      .hasName("JavaScriptBootstrapUI.connectClient")
                                      .hasParent(spans.get(spans.size() - 2))
                                      .hasKind(SpanKind.INTERNAL);
                                });
                      });
            });
  }
}
