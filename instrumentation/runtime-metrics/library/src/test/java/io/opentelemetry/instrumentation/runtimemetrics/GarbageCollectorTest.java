/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import static io.opentelemetry.instrumentation.runtimemetrics.ScopeUtil.EXPECTED_SCOPE;
import static io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat;
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
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;
import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GarbageCollectorTest {

  @RegisterExtension
  static final InstrumentationExtension testing = LibraryInstrumentationExtension.create();

  @Mock private GarbageCollectorMXBean gcBean;
  @Mock private NotificationEmitter notificationEmitter;

  private GarbageCollectorMXBean testGcBean;

  @BeforeEach
  void setup() {
    testGcBean = new TestGcBean(notificationEmitter, gcBean);
  }

  @Test
  void registerObservers() {
    GarbageCollector.registerObservers(
        testing.getOpenTelemetry(),
        Collections.singletonList(testGcBean),
        GarbageCollectorTest::getGcNotificationInfo);

    ArgumentCaptor<NotificationListener> listenerCaptor =
        ArgumentCaptor.forClass(NotificationListener.class);
    verify(notificationEmitter).addNotificationListener(listenerCaptor.capture(), any(), any());
    NotificationListener listener = listenerCaptor.getValue();

    listener.handleNotification(
        createTestNotification("G1 Young Generation", "end of minor GC", 10), null);
    listener.handleNotification(
        createTestNotification("G1 Young Generation", "end of minor GC", 12), null);
    listener.handleNotification(
        createTestNotification("G1 Old Generation", "end of major GC", 11), null);

    testing.waitAndAssertMetrics(
        "io.opentelemetry.runtime-metrics",
        "process.runtime.jvm.gc.time",
        metrics ->
            metrics.anySatisfy(
                metricData ->
                    assertThat(metricData)
                        .hasInstrumentationScope(EXPECTED_SCOPE)
                        .hasDescription("Time spent performing JVM garbage collection actions")
                        .hasUnit("ms")
                        .hasHistogramSatisfying(
                            histogram ->
                                histogram.hasPointsSatisfying(
                                    point ->
                                        point
                                            .hasCount(2)
                                            .hasSum(22)
                                            .hasAttributes(
                                                Attributes.builder()
                                                    .put("gc", "G1 Young Generation")
                                                    .put("action", "end of minor GC")
                                                    .build()),
                                    point ->
                                        point
                                            .hasCount(1)
                                            .hasSum(11)
                                            .hasAttributes(
                                                Attributes.builder()
                                                    .put("gc", "G1 Old Generation")
                                                    .put("action", "end of major GC")
                                                    .build())))));
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

  /**
   * Combines {@link NotificationEmitter} and {@link GarbageCollectorMXBean}, allowing a composite
   * mock of the two interfaces.
   */
  private static class TestGcBean implements NotificationEmitter, GarbageCollectorMXBean {

    private final NotificationEmitter notificationEmitter;
    private final GarbageCollectorMXBean gcBean;

    private TestGcBean(NotificationEmitter notificationEmitter, GarbageCollectorMXBean gcBean) {
      this.notificationEmitter = notificationEmitter;
      this.gcBean = gcBean;
    }

    @Override
    public void removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {
      notificationEmitter.removeNotificationListener(listener);
    }

    @Override
    public void removeNotificationListener(
        NotificationListener listener, NotificationFilter filter, Object handback)
        throws ListenerNotFoundException {
      notificationEmitter.removeNotificationListener(listener, filter, handback);
    }

    @Override
    public void addNotificationListener(
        NotificationListener listener, NotificationFilter filter, Object handback) {
      notificationEmitter.addNotificationListener(listener, filter, handback);
    }

    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {
      return notificationEmitter.getNotificationInfo();
    }

    @Override
    public long getCollectionCount() {
      return gcBean.getCollectionCount();
    }

    @Override
    public long getCollectionTime() {
      return gcBean.getCollectionTime();
    }

    @Override
    public String getName() {
      return gcBean.getName();
    }

    @Override
    public boolean isValid() {
      return gcBean.isValid();
    }

    @Override
    public String[] getMemoryPoolNames() {
      return gcBean.getMemoryPoolNames();
    }

    @Override
    public ObjectName getObjectName() {
      return gcBean.getObjectName();
    }
  }
}
