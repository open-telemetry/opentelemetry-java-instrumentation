/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.runtimemetrics;

import com.sun.management.GarbageCollectionNotificationInfo;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongHistogram;
import io.opentelemetry.api.metrics.Meter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.util.List;
import javax.management.Notification;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.openmbean.CompositeData;

public final class Gc {

  // Visible for testing
  static final Gc INSTANCE = new Gc();

  private static final AttributeKey<String> GC_KEY = AttributeKey.stringKey("gc");
  private static final AttributeKey<String> CAUSE_KEY = AttributeKey.stringKey("cause");
  private static final AttributeKey<String> ACTION_KEY = AttributeKey.stringKey("action");

  private static final NotificationFilter GC_FILTER =
      notification ->
          notification
              .getType()
              .equals(GarbageCollectionNotificationInfo.GARBAGE_COLLECTION_NOTIFICATION);

  public static void registerObservers(OpenTelemetry openTelemetry) {
    INSTANCE.registerObservers(openTelemetry, ManagementFactory.getGarbageCollectorMXBeans());
  }

  // Visible for testing
  void registerObservers(OpenTelemetry openTelemetry, List<GarbageCollectorMXBean> gcBeans) {
    Meter meter = openTelemetry.getMeter("io.opentelemetry.runtime-metrics");

    LongHistogram gcTime =
        meter
            .histogramBuilder("process.runtime.jvm.gc.time")
            .setDescription("Time spent performing JVM garbage collection actions")
            .setUnit("ms")
            .ofLongs()
            .build();

    for (GarbageCollectorMXBean gcBean : ManagementFactory.getGarbageCollectorMXBeans()) {
      if (!(gcBean instanceof NotificationEmitter)) {
        continue;
      }
      NotificationEmitter notificationEmitter = (NotificationEmitter) gcBean;
      notificationEmitter.addNotificationListener(
          new GcNotificationListener(gcTime), GC_FILTER, null);
    }
  }

  private static final class GcNotificationListener implements NotificationListener {

    private final LongHistogram gcTime;

    private GcNotificationListener(LongHistogram gcTime) {
      this.gcTime = gcTime;
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
      GarbageCollectionNotificationInfo notificationInfo =
          GarbageCollectionNotificationInfo.from((CompositeData) notification.getUserData());
      gcTime.record(
          notificationInfo.getGcInfo().getDuration(),
          Attributes.of(
              GC_KEY,
              notificationInfo.getGcName(),
              CAUSE_KEY,
              notificationInfo.getGcCause(),
              ACTION_KEY,
              notificationInfo.getGcAction()));
    }
  }

  private Gc() {}
}
