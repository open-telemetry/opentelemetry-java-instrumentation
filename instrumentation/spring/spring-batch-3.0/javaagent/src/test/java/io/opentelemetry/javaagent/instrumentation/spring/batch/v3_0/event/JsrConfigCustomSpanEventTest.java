/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.event;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.javaagent.instrumentation.spring.batch.v3_0.runner.JavaxBatchConfigRunner;
import io.opentelemetry.sdk.testing.assertj.SpanDataAssert;
import io.opentelemetry.sdk.testing.assertj.TraceAssert;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.extension.RegisterExtension;

class JsrConfigCustomSpanEventTest extends CustomSpanEventTest {

  @RegisterExtension static final JavaxBatchConfigRunner runner = new JavaxBatchConfigRunner();

  JsrConfigCustomSpanEventTest() {
    super(runner);
  }

  @Override
  protected void itemSpans(TraceAssert trace, List<Consumer<SpanDataAssert>> assertions) {
    assertions.addAll(
        Arrays.asList(
            span ->
                span.hasName("BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemRead")
                    .hasKind(SpanKind.INTERNAL)
                    .hasParent(trace.getSpan(2))
                    .hasEventsSatisfyingExactly(
                        event -> event.hasName("item.read.before"),
                        event -> event.hasName("item.read.after")),
            span ->
                span.hasName(
                        "BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemProcess")
                    .hasKind(SpanKind.INTERNAL)
                    .hasParent(trace.getSpan(2))
                    .hasEventsSatisfyingExactly(
                        event -> event.hasName("item.process.before"),
                        event -> event.hasName("item.process.after")),
            span ->
                span.hasName("BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemRead")
                    .hasKind(SpanKind.INTERNAL)
                    .hasParent(trace.getSpan(2))
                    .hasEventsSatisfyingExactly(event -> event.hasName("item.read.before")),
            span ->
                span.hasName("BatchJob customSpanEventsItemsJob.customSpanEventsItemStep.ItemWrite")
                    .hasKind(SpanKind.INTERNAL)
                    .hasParent(trace.getSpan(2))
                    .hasEventsSatisfyingExactly(
                        event -> event.hasName("item.write.before"),
                        event -> event.hasName("item.write.after"))));
  }
}
