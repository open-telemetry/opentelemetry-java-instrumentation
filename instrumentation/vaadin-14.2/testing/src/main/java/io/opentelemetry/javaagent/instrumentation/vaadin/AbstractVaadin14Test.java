/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.vaadin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions;
import io.opentelemetry.sdk.trace.data.SpanData;
import java.util.List;

public class AbstractVaadin14Test extends AbstractVaadinTest {
  @Override
  void assertFirstRequest() {
    await()
        .untilAsserted(
            () -> {
              List<List<SpanData>> traces = testing.waitForTraces(1);
              assertThat(traces.get(0))
                  .satisfies(
                      spans -> {
                        OpenTelemetryAssertions.assertThat(spans.get(0))
                            .hasName(getContextPath() + "/main")
                            .hasNoParent()
                            .hasKind(SpanKind.SERVER);
                        OpenTelemetryAssertions.assertThat(spans.get(1))
                            .hasName("SpringVaadinServletService.handleRequest")
                            .hasParent(spans.get(0))
                            .hasKind(SpanKind.INTERNAL);
                        // we don't assert all the handler spans as these vary between
                        // vaadin versions
                        OpenTelemetryAssertions.assertThat(spans.get(spans.size() - 1))
                            .hasName("BootstrapHandler.handleRequest")
                            .hasParent(spans.get(1))
                            .hasKind(SpanKind.INTERNAL);
                      });
            });
  }
}
