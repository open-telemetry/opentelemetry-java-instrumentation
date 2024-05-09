/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics.java8;

import static java.util.Arrays.asList;
import static java.util.Collections.unmodifiableList;

import com.sun.management.GarbageCollectionNotificationInfo;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.instrumentation.runtimemetrics.java8.internal.JmxRuntimeMetricsUtil;
import io.opentelemetry.semconv.JvmAttributes;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.logging.Logger;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

/**
 * Registers instruments that generate metrics about JVM garbage collection. The metrics generated
 * by this class follow <a
 * href="https://github.com/open-telemetry/semantic-conventions/blob/main/docs/runtime/jvm-metrics.md">the
 * stable JVM metrics semantic conventions</a>.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * GarbageCollector.registerObservers(GlobalOpenTelemetry.get());
 * }</pre>
 *
 * <p>Example metrics being exported:
 *
 * <pre>
 *   jvm.gc.duration{jvm.gc.name="G1 Young Generation",jvm.gc.action="end of minor GC"} 0.022
 * </pre>
 */
public final class GarbageCollector {

  private static final Logger logger = Logger.getLogger(GarbageCollector.class.getName());

  private static final double MILLIS_PER_S = TimeUnit.SECONDS.toMillis(1);

  static final List<Double> GC_DURATION_BUCKETS = unmodifiableList(asList(0.01, 0.1, 1., 10.));

  private static final NotificationFilter GC_FILTER =
      notification ->
          notification
              .getType()
              .equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION);

  /** Register observers for java runtime memory metrics. */
  public static List<AutoCloseable> registerObservers(OpenTelemetry openTelemetry) {
    if (!isNotificationClassPresent()) {
      logger.fine(
          "The com.sun.management.GarbageCollectionNotificationInfo class is not available;"
              + " GC metrics will not be reported.");
      return Collections.emptyList();
    }

    return registerObservers(
        openTelemetry,
        ManagementFactory.getGarbageCollectorMXBeans(),
        GarbageCollector::extractNotificationInfo);
  }

  // Visible for testing
  static List<AutoCloseable> registerObservers(
      OpenTelemetry openTelemetry,
      List<GarbageCollectorMXBean> gcBeans,
      Function<Notification, GarbageCollectionNotificationInfo> notificationInfoExtractor) {
    Meter meter = JmxRuntimeMetricsUtil.getMeter(openTelemetry);

    DoubleHistogram gcDuration =
        meter
            .histogramBuilder("jvm.gc.duration")
            .setDescription("Duration of JVM garbage collection actions.")
            .setUnit("s")
            .setExplicitBucketBoundariesAdvice(GC_DURATION_BUCKETS)
            .build();

    List<AutoCloseable> result = new ArrayList<>();
    for (GarbageCollectorMXBean gcBean : gcBeans) {
      if (!(gcBean instanceof NotificationEmitter)) {
        continue;
      }
      NotificationEmitter notificationEmitter = (NotificationEmitter) gcBean;
      GcNotificationListener listener =
          new GcNotificationListener(gcDuration, notificationInfoExtractor);
      notificationEmitter.addNotificationListener(listener, GC_FILTER, null);
      result.add(() -> notificationEmitter.removeNotificationListener(listener));
    }
    return result;
  }

  private static final class GcNotificationListener implements NotificationListener {

    private final DoubleHistogram gcDuration;
    private final Function<Notification, GarbageCollectionNotificationInfo>
        notificationInfoExtractor;

    private GcNotificationListener(
        DoubleHistogram gcDuration,
        Function<Notification, GarbageCollectionNotificationInfo> notificationInfoExtractor) {
      this.gcDuration = gcDuration;
      this.notificationInfoExtractor = notificationInfoExtractor;
    }

    @Override
    public void handleNotification(Notification notification, Object unused) {
      GarbageCollectionNotificationInfo notificationInfo =
          notificationInfoExtractor.apply(notification);

      String gcName = notificationInfo.getGcName();
      String gcAction = notificationInfo.getGcAction();
      double duration = notificationInfo.getGcInfo().getDuration() / MILLIS_PER_S;

      gcDuration.record(
          duration,
          Attributes.of(JvmAttributes.JVM_GC_NAME, gcName, JvmAttributes.JVM_GC_ACTION, gcAction));
    }
  }

  /**
   * Extract {@link GarbageCollectionNotificationInfo} from the {@link Notification}.
   *
   * <p>Note: this exists as a separate function so that the behavior can be overridden with mocks
   * in tests. It's very challenging to create a mock {@link CompositeData} that can be parsed by
   * {@link GarbageCollectionNotificationInfo#from(CompositeData)}.
   */
  private static GarbageCollectionNotificationInfo extractNotificationInfo(
      Notification notification) {
    return GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
  }

  private static boolean isNotificationClassPresent() {
    try {
      Class.forName(
          "com.sun.management.GarbageCollectionNotificationInfo",
          false,
          GarbageCollectorMXBean.class.getClassLoader());
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }

  private GarbageCollector() {}
}
