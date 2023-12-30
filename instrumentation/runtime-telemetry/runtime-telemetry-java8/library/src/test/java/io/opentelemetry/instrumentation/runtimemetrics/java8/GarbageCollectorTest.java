/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8;

import static io.opentelemetry.instrumentation.runtimemetrics.java8.ScopeUtil.EXPECTED_SCOPE;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sun.management.GarbageCollectionNotificationInfo;
import com.sun.management.GcInfo;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.instrumentation.testing.junit.InstrumentationExtension;
import io.opentelemetry.instrumentation.testing.junit.LibraryInstrumentationExtension;
import java.lang.management.GarbageCollectorMXBean;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationListener;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GarbageCollectorTest {

  static final double[] GC_DURATION_BUCKETS =
      GarbageCollector.GC_DURATION_BUCKETS.stream().mapToDouble(d -> d).toArray();

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock(extraInterfaces = NotificationEmitter.class)
  private GarbageCollectorMXBean gcBean;

  @Captor private ArgumentCaptor<NotificationListener> listenerCaptor;

  @Test
  void registerObservers() {
    GarbageCollector.registerObservers(
        testing.getOpenTelemetry(),
        singletonList(gcBean),
        GarbageCollectorTest::getGcNotificationInfo);

    NotificationEmitter notificationEmitter = (NotificationEmitter) gcBean;
    verify(notificationEmitter).addNotificationListener(listenerCaptor.capture(), any(), any());
    NotificationListener listener = listenerCaptor.getValue();

    listener.handleNotification(
        createTestNotification("G1 Young Generation", "end of minor GC", 10), null);
    listener.handleNotification(
        createTestNotification("G1 Young Generation", "end of minor GC", 12), null);
    listener.handleNotification(
        createTestNotification("G1 Old Generation", "end of major GC", 11), null);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-telemetry-java8",
        "jvm.gc.duration",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasInstrumentationScope(EXPECTED_SCOPE)
                        .hasDescription("Duration of JVM garbage collection actions.")
                        .hasUnit("s")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasCount(2)
                                            .hasSum(0.022)
                                            .hasAttributes(
                                                Attributes.builder()
                                                    .put("jvm.gc.name", "G1 Young Generation")
                                                    .put("jvm.gc.action", "end of minor GC")
                                                    .build())
                                            .hasBucketBoundaries(GC_DURATION_BUCKETS),
                                    point ->
                                        point
                                            .hasCount(1)
                                            .hasSum(0.011)
                                            .hasAttributes(
                                                Attributes.builder()
                                                    .put("jvm.gc.name", "G1 Old Generation")
                                                    .put("jvm.gc.action", "end of major GC")
                                                    .build())
                                            .hasBucketBoundaries(GC_DURATION_BUCKETS)))));
  }

  private static Notification createTestNotification(
      String gcName, String gcAction, long duration) {
    GarbageCollectionNotificationInfo gcNotificationInfo =
        mock(GarbageCollectionNotificationInfo.class);
    when(gcNotificationInfo.getGcName()).thenReturn(gcName);
    when(gcNotificationInfo.getGcAction()).thenReturn(gcAction);
    GcInfo gcInfo = mock(GcInfo.class);
    when(gcInfo.getDuration()).thenReturn(duration);
    when(gcNotificationInfo.getGcInfo()).thenReturn(gcInfo);
    return new TestNotification(gcNotificationInfo);
  }

  private static GarbageCollectionNotificationInfo getGcNotificationInfo(
      Notification notification) {
    return ((TestNotification) notification).gcNotificationInfo;
  }

  /**
   * A {@link Notification} when is initialized with a mock {@link
   * GarbageCollectionNotificationInfo}.
   */
  private static class TestNotification extends Notification {

    private static final AtomicLong sequence = new AtomicLong(0);

    private final GarbageCollectionNotificationInfo gcNotificationInfo;

    private TestNotification(GarbageCollectionNotificationInfo gcNotificationInfo) {
      super(
          GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION,
          "test",
          sequence.incrementAndGet());
      this.gcNotificationInfo = gcNotificationInfo;
    }
  }
}
