/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.iceberg.v1_8;

import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;

final class ScanMetricsBuilder {
  private static final String ROOT = "iceberg.scan";
  private static final String TOTAL_PLANNING_DURATION = ROOT + ".planning.duration";
  private static final String RESULT_DATA_FILES = ROOT + ".scanned.data_files.count";
  private static final String RESULT_DELETE_FILES = ROOT + ".scanned.delete_files.count";
  private static final String SCANNED_DATA_MANIFESTS = ROOT + ".scanned.data_manifests.count";
  private static final String SCANNED_DELETE_MANIFESTS = ROOT + ".scanned.delete_manifests.count";
  private static final String TOTAL_DATA_MANIFESTS = ROOT + ".total.data_manifests.count";
  private static final String TOTAL_DELETE_MANIFESTS = ROOT + ".total.delete_manifests.count";
  private static final String TOTAL_FILE_SIZE_IN_BYTES = ROOT + ".scanned.data_files.size";
  private static final String TOTAL_DELETE_FILE_SIZE_IN_BYTES = ROOT + "scanned.delete_files.size";
  private static final String SKIPPED_DATA_MANIFESTS = ROOT + ".skipped.data_manifests.count";
  private static final String SKIPPED_DELETE_MANIFESTS = ROOT + ".skipped.delete_manifests.count";
  private static final String SKIPPED_DATA_FILES = ROOT + ".skipped.data_files.count";
  private static final String SKIPPED_DELETE_FILES = ROOT + ".skipped.delete_files.count";
  private static final String INDEXED_DELETE_FILES = ROOT + ".scanned.indexed_delete_files.count";
  private static final String EQUALITY_DELETE_FILES = ROOT + ".scanned.equality_delete_files.count";
  private static final String POSITIONAL_DELETE_FILES =
      ROOT + ".scanned.positional_delete_files.count";
  private static final String DVS = ROOT + ".scanned.dvs.count";

  private ScanMetricsBuilder() {
    // prevents instantiation
  }

  static LongGauge totalPlanningDuration(Meter meter) {
    return meter
        .gaugeBuilder(TOTAL_PLANNING_DURATION)
        .setDescription("The total duration needed to plan the scan.")
        .setUnit("ms")
        .ofLongs()
        .build();
  }

  static LongCounter scannedDataFilesCount(Meter meter) {
    return meter
        .counterBuilder(RESULT_DATA_FILES)
        .setDescription("The number of scanned data files.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter scannedDeleteFilesCount(Meter meter) {
    return meter
        .counterBuilder(RESULT_DELETE_FILES)
        .setDescription("The number of scanned delete files.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter scannedDataManifestsCount(Meter meter) {
    return meter
        .counterBuilder(SCANNED_DATA_MANIFESTS)
        .setDescription("The number of scanned data manifests.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter scannedDeleteManifestsCount(Meter meter) {
    return meter
        .counterBuilder(SCANNED_DELETE_MANIFESTS)
        .setDescription("The number of scanned delete manifests.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter totalDataManifestsCount(Meter meter) {
    return meter
        .counterBuilder(TOTAL_DATA_MANIFESTS)
        .setDescription("The number of all data manifests.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter totalDeleteManifestsCount(Meter meter) {
    return meter
        .counterBuilder(TOTAL_DELETE_MANIFESTS)
        .setDescription("The number of all delete manifests.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter scannedDataFilesSize(Meter meter) {
    return meter
        .counterBuilder(TOTAL_FILE_SIZE_IN_BYTES)
        .setDescription("The total size of all scanned data files.")
        .setUnit("byte")
        .build();
  }

  static LongCounter scannedDeleteFilesSize(Meter meter) {
    return meter
        .counterBuilder(TOTAL_DELETE_FILE_SIZE_IN_BYTES)
        .setDescription("The total size of all scanned delete files.")
        .setUnit("byte")
        .build();
  }

  static LongCounter skippedDataManifestsCount(Meter meter) {
    return meter
        .counterBuilder(SKIPPED_DATA_MANIFESTS)
        .setDescription("The number of data manifests that were skipped during the scan.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter skippedDeleteManifestsCount(Meter meter) {
    return meter
        .counterBuilder(SKIPPED_DELETE_MANIFESTS)
        .setDescription("The number of delete manifests that were skipped during the scan.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter skippedDataFilesCount(Meter meter) {
    return meter
        .counterBuilder(SKIPPED_DATA_FILES)
        .setDescription("The number of data files that were skipped during the scan.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter skippedDeleteFilesCount(Meter meter) {
    return meter
        .counterBuilder(SKIPPED_DELETE_FILES)
        .setDescription("The number of delete files that were skipped during the scan.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter indexedDeleteFilesCount(Meter meter) {
    return meter
        .counterBuilder(INDEXED_DELETE_FILES)
        .setDescription(
            "The number of delete files constituting the delete file index for this scan.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter equalityDeleteFilesCount(Meter meter) {
    return meter
        .counterBuilder(EQUALITY_DELETE_FILES)
        .setDescription("The number of equality delete files relevant for the current scan.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter positionDeleteFilesCount(Meter meter) {
    return meter
        .counterBuilder(POSITIONAL_DELETE_FILES)
        .setDescription("The number of position delete files relevant for the current scan.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter deletionVectorFilesCount(Meter meter) {
    return meter
        .counterBuilder(DVS)
        .setDescription("The number of deletion vector (DV) files relevant for the current scan.")
        .setUnit("{file}")
        .build();
  }
}
