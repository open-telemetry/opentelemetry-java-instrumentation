/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package opentlelemetry.instrumentation.iceberg.v1_6;

import io.opentelemetry.api.metrics.DoubleGauge;
import io.opentelemetry.api.metrics.Meter;

public class ScanMetricsBuilder {
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

  static DoubleGauge totalPlanningDuration(Meter meter) {
    return meter
        .gaugeBuilder(TOTAL_PLANNING_DURATION)
        .setDescription("The total duration needed to plan the scan.")
        .setUnit("ms")
        .build();
  }

  static DoubleGauge scannedDataFilesCount(Meter meter) {
    return meter
        .gaugeBuilder(RESULT_DATA_FILES)
        .setDescription("The number of scanned data files.")
        .setUnit("{file}")
        .build();
  }

  static DoubleGauge scannedDeleteFilesCount(Meter meter) {
    return meter
        .gaugeBuilder(RESULT_DELETE_FILES)
        .setDescription("The number of scanned delete files.")
        .setUnit("{file}")
        .build();
  }

  static DoubleGauge scannedDataManifestsCount(Meter meter) {
    return meter
        .gaugeBuilder(SCANNED_DATA_MANIFESTS)
        .setDescription("The number of scanned data manifests.")
        .setUnit("{file}")
        .build();
  }

  static DoubleGauge scannedDeleteManifestsCount(Meter meter) {
    return meter
        .gaugeBuilder(SCANNED_DELETE_MANIFESTS)
        .setDescription("The number of scanned delete manifests.")
        .setUnit("{file}")
        .build();
  }

  static DoubleGauge totalDataManifestsCount(Meter meter) {
    return meter
        .gaugeBuilder(TOTAL_DATA_MANIFESTS)
        .setDescription("The number of all data manifests.")
        .setUnit("{file}")
        .build();
  }

  static DoubleGauge totalDeleteManifestsCount(Meter meter) {
    return meter
        .gaugeBuilder(TOTAL_DELETE_MANIFESTS)
        .setDescription("The number of all delete manifests.")
        .setUnit("{file}")
        .build();
  }

  static DoubleGauge scannedDataFilesSize(Meter meter) {
    return meter
        .gaugeBuilder(TOTAL_FILE_SIZE_IN_BYTES)
        .setDescription("The total size of all scanned data files.")
        .setUnit("byte")
        .build();
  }

  static DoubleGauge scannedDeleteFilesSize(Meter meter) {
    return meter
        .gaugeBuilder(TOTAL_DELETE_FILE_SIZE_IN_BYTES)
        .setDescription("The total size of all scanned delete files.")
        .setUnit("byte")
        .build();
  }

  static DoubleGauge skippedDataManifests(Meter meter) {
    return meter
        .gaugeBuilder(SKIPPED_DATA_MANIFESTS)
        .setDescription("The number of data manifests that were skipped during the scan.")
        .setUnit("{file}")
        .build();
  }

  static DoubleGauge skippedDeleteManifests(Meter meter) {
    return meter
        .gaugeBuilder(SKIPPED_DELETE_MANIFESTS)
        .setDescription("The number of delete manifests that were skipped during the scan.")
        .setUnit("{file}")
        .build();
  }

  static DoubleGauge skippedDataFiles(Meter meter) {
    return meter
        .gaugeBuilder(SKIPPED_DATA_FILES)
        .setDescription("The number of data files that were skipped during the scan.")
        .setUnit("{file}")
        .build();
  }

  static DoubleGauge skippedDeleteFiles(Meter meter) {
    return meter
        .gaugeBuilder(SKIPPED_DELETE_FILES)
        .setDescription("The number of delete files that were skipped during the scan.")
        .setUnit("{file}")
        .build();
  }

  static DoubleGauge indexedDeleteFiles(Meter meter) {
    return meter
        .gaugeBuilder(INDEXED_DELETE_FILES)
        .setDescription(
            "The number of delete files constituting the delete file index for this scan.")
        .setUnit("{file}")
        .build();
  }

  static DoubleGauge equalityDeleteFiles(Meter meter) {
    return meter
        .gaugeBuilder(EQUALITY_DELETE_FILES)
        .setDescription("The number of equality delete files relevant for the current scan.")
        .setUnit("{file}")
        .build();
  }

  static DoubleGauge positionDeleteFiles(Meter meter) {
    return meter
        .gaugeBuilder(POSITIONAL_DELETE_FILES)
        .setDescription("The number of position delete files relevant for the current scan.")
        .setUnit("{file}")
        .build();
  }

  static DoubleGauge deletionVectorFiles(Meter meter) {
    return meter
        .gaugeBuilder(DVS)
        .setDescription("The number of deletion vector (DV) files relevant for the current scan.")
        .setUnit("{file}")
        .build();
  }
}
