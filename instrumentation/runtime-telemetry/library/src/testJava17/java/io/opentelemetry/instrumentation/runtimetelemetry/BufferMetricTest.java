/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimetelemetry;

import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.BYTES;
import static io.opentelemetry.instrumentation.runtimetelemetry.internal.Constants.UNIT_BUFFERS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.runtimetelemetry.internal.JfrFeature;
import java.nio.ByteBuffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

class BufferMetricTest {

  @RegisterExtension
  JfrExtension jfrExtension =
      new JfrExtension(
          jfrConfig -> {
            jfrConfig.disableAllFeatures();
            jfrConfig.enableFeature(JfrFeature.BUFFER_METRICS);
          });

  /**
   * This is a basic test that allocates some buffers and tests to make sure the resulting JFR event
   * was handled and turned into the expected metrics.
   *
   * <p>This test handles all 3 buffer related metrics defined in the OpenTelemetry Java runtime
   * Semantic Conventions.
   *
   * <p>Currently JFR only has support for the "direct" buffer pool. The "mapped" and "mapped -
   * 'non-volatile memory'" pools do not have corresponding JFR events. In the future, events should
   * be added for those missing pools.
   */
  @Test
  void shouldHaveJfrLoadedClassesCountEvents() {
    ByteBuffer buffer = ByteBuffer.allocateDirect(10000);
    buffer.put("test".getBytes(UTF_8));

    AttributeKey<String> attrBufferPool = AttributeKey.stringKey("jvm.buffer.pool.name");
    Attributes directBuffer = Attributes.of(attrBufferPool, "direct");
    jfrExtension.waitAndAssertMetrics(
        metric ->
            metric
                .hasName("jvm.buffer.count")
                .hasDescription("Number of buffers in the pool.")
                .hasUnit(UNIT_BUFFERS)
                .hasLongSumSatisfying(
                    sum ->
                        sum.hasPointsSatisfying(
                            point ->
                                point.satisfies(
                                    pointData -> {
                                      assertThat(pointData.getValue()).isGreaterThan(0);
                                      assertThat(pointData.getAttributes()).isEqualTo(directBuffer);
                                    }))),
        metric ->
            metric
                .hasName("jvm.buffer.memory.limit")
                .hasDescription("Measure of total memory capacity of buffers.")
                .hasUnit(BYTES)
                .hasLongSumSatisfying(
                    sum ->
                        sum.hasPointsSatisfying(
                            point ->
                                point.satisfies(
                                    pointData -> {
                                      assertThat(pointData.getValue()).isGreaterThan(0);
                                      assertThat(pointData.getAttributes()).isEqualTo(directBuffer);
                                    }))),
        metric ->
            metric
                .hasName("jvm.buffer.memory.used")
                .hasDescription("Measure of memory used by buffers.")
                .hasUnit(BYTES)
                .hasLongSumSatisfying(
                    sum ->
                        sum.hasPointsSatisfying(
                            point ->
                                point.satisfies(
                                    pointData -> {
                                      assertThat(pointData.getValue()).isGreaterThan(0);
                                      assertThat(pointData.getAttributes()).isEqualTo(directBuffer);
                                    }))));
  }
}
