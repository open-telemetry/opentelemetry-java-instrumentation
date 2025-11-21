/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.iceberg.v1_8;

import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;

final class ScanMetricsBuilder {
  private static final String ROOT = "iceberg.scan";
  private static final String TOTAL_PLANNING_DURATION = ROOT + ".planning.duration";

  private static final String DATA_FILES_COUNT = ROOT + ".data_files.count";
  private static final String DATA_FILES_SIZE = ROOT + ".data_files.size";
  private static final String DELETE_FILES_SIZE = ROOT + ".delete_files.size";
  private static final String DELETE_FILES_COUNT = ROOT + ".delete_files.count";
  private static final String DATA_MANIFESTS_COUNT = ROOT + ".data_manifests.count";
  private static final String DELETE_MANIFESTS_COUNT = ROOT + ".delete_manifests.count";

  private ScanMetricsBuilder() {
    // prevents instantiation
  }

  static DoubleHistogram totalPlanningDuration(Meter meter, String unit) {
    return meter
        .histogramBuilder(TOTAL_PLANNING_DURATION)
        .setDescription("The total duration needed to plan the scan.")
        .setUnit(unit)
        .build();
  }

  static LongCounter dataFilesCount(Meter meter) {
    return meter
        .counterBuilder(DATA_FILES_COUNT)
        .setDescription("The number of data files.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter deleteFilesCount(Meter meter) {
    return meter
        .counterBuilder(DELETE_FILES_COUNT)
        .setDescription("The number of delete files.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter dataManifestsCount(Meter meter) {
    return meter
        .counterBuilder(DATA_MANIFESTS_COUNT)
        .setDescription("The number of data manifests.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter deleteManifestsCount(Meter meter) {
    return meter
        .counterBuilder(DELETE_MANIFESTS_COUNT)
        .setDescription("The number of delete manifests.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter dataFilesSize(Meter meter) {
    return meter
        .counterBuilder(DATA_FILES_SIZE)
        .setDescription("The total size of all scanned data files.")
        .setUnit("By")
        .build();
  }

  static LongCounter deleteFilesSize(Meter meter) {
    return meter
        .counterBuilder(DELETE_FILES_SIZE)
        .setDescription("The total size of all scanned delete files.")
        .setUnit("By")
        .build();
  }
}
