/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.jmx.internal.handler;

import static java.util.logging.Level.WARNING;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.metrics.ObservableLongMeasurement;
import io.opentelemetry.instrumentation.jmx.internal.ExperimentalJmxMetricHandler;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import javax.management.MBeanServerConnection;
import javax.management.ObjectName;

/**
 * JMX metric handler that reports per-compaction byte progress from Cassandra's CompactionManager.
 *
 * <p>Queries {@code org.apache.cassandra.db:type=CompactionManager}, reads the {@code Compactions}
 * attribute (a list of maps representing in-flight compactions), groups entries by {@code taskType
 * + keyspace + columnfamily}, and emits two gauges per group:
 *
 * <ul>
 *   <li>{@code cassandra.compaction.progress.bytes} — bytes completed so far
 *   <li>{@code cassandra.compaction.progress.total} — total bytes for the compaction
 * </ul>
 *
 * <p>Both metrics carry {@code cassandra.compaction.task_type}, {@code cassandra.keyspace}, and
 * {@code cassandra.table} attributes. Entries missing any of these dimension fields are skipped.
 * Byte values are string-encoded in the MBean and parsed via {@link BigInteger}.
 *
 * <p>Note: these are distinct from {@code cassandra.compaction.tasks.pending/completed}, which are
 * simple scalar task counts from {@code org.apache.cassandra.metrics:type=Compaction}.
 *
 * <p>This class is internal and is hence not for public use. Its APIs are unstable and can change
 * at any time.
 */
public final class CassandraCompactionProgressHandler implements ExperimentalJmxMetricHandler {

  static final String HANDLER_NAME = "cassandra-compaction-progress";
  static final String METRIC_CURRENT = "cassandra.compaction.progress.bytes";
  static final String METRIC_TOTAL = "cassandra.compaction.progress.total";

  private static final String ATTR_TASK_TYPE = "cassandra.compaction.task_type";
  private static final String ATTR_KEYSPACE = "cassandra.keyspace";
  private static final String ATTR_TABLE = "cassandra.table";

  private static final Logger logger =
      Logger.getLogger(CassandraCompactionProgressHandler.class.getName());

  @Override
  public String getName() {
    return HANDLER_NAME;
  }

  @Override
  public AutoCloseable create(Meter meter, Supplier<Detector> detectorSupplier) {
    ObservableLongMeasurement currentGauge =
        meter
            .gaugeBuilder(METRIC_CURRENT)
            .setDescription("Bytes completed for in-flight compactions")
            .setUnit("By")
            .ofLongs()
            .buildObserver();

    ObservableLongMeasurement totalGauge =
        meter
            .gaugeBuilder(METRIC_TOTAL)
            .setDescription("Total bytes for in-flight compactions")
            .setUnit("By")
            .ofLongs()
            .buildObserver();

    return meter.batchCallback(
        () -> {
          for (Map.Entry<Attributes, long[]> entry : queryGroups(detectorSupplier).entrySet()) {
            currentGauge.record(entry.getValue()[0], entry.getKey());
            totalGauge.record(entry.getValue()[1], entry.getKey());
          }
        },
        currentGauge,
        totalGauge);
  }

  private static Map<Attributes, long[]> queryGroups(Supplier<Detector> detectorSupplier) {
    Map<Attributes, long[]> groups = new HashMap<>();
    Detector detector = detectorSupplier.get();
    if (detector == null) {
      return groups;
    }
    MBeanServerConnection connection = detector.getConnection();
    for (ObjectName objectName : detector.getObjectNames()) {
      queryCompactions(connection, objectName)
          .forEach(
              (attrs, values) ->
                  groups.merge(attrs, values, (a, b) -> new long[] {a[0] + b[0], a[1] + b[1]}));
    }
    return groups;
  }

  static Map<Attributes, long[]> queryCompactions(
      MBeanServerConnection connection, ObjectName objectName) {
    Map<Attributes, long[]> groups = new HashMap<>();
    try {
      // CompactionManager#getCompactions() returns List<Map<String,String>> via Standard MBean
      @SuppressWarnings("unchecked")
      List<Map<String, String>> compactions =
          (List<Map<String, String>>) connection.getAttribute(objectName, "Compactions");

      for (Map<String, String> entry : compactions) {
        String taskType = entry.get("taskType");
        String keyspace = entry.get("keyspace");
        String columnFamily = entry.get("columnfamily");
        String unit = entry.get("unit");

        if (taskType == null || keyspace == null || columnFamily == null || !isByteUnit(unit)) {
          continue;
        }

        long completed = parseLong(entry.get("completed"));
        long total = parseLong(entry.get("total"));

        Attributes attrs = buildAttributes(taskType, keyspace, columnFamily);
        groups.merge(
            attrs, new long[] {completed, total}, (a, b) -> new long[] {a[0] + b[0], a[1] + b[1]});
      }
    } catch (Exception e) {
      logger.log(WARNING, "cassandra.compaction.progress: failed to query CompactionManager", e);
    }
    return groups;
  }

  private static boolean isByteUnit(@Nullable String unit) {
    return unit != null && unit.equalsIgnoreCase("bytes");
  }

  private static long parseLong(@Nullable Object value) {
    if (value == null) {
      return 0;
    }
    try {
      return new BigInteger(value.toString()).longValue();
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static Attributes buildAttributes(String taskType, String keyspace, String columnFamily) {
    AttributesBuilder builder = Attributes.builder();
    builder.put(ATTR_TASK_TYPE, taskType);
    builder.put(ATTR_KEYSPACE, keyspace);
    builder.put(ATTR_TABLE, columnFamily);
    return builder.build();
  }
}
