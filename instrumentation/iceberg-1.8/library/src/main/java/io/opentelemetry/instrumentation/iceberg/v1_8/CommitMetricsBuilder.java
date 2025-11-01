/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.iceberg.v1_8;

import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.LongGauge;
import io.opentelemetry.api.metrics.Meter;

final class CommitMetricsBuilder {

  private static final String ROOT = "iceberg.commit";
  private static final String DURATION = ROOT + ".duration";
  private static final String ATTEMPTS = ROOT + ".attempts.count";
  private static final String ADDED_DATA_FILES = ROOT + ".added.data_files.count";
  private static final String REMOVED_DATA_FILES = ROOT + ".removed.data_files.count";
  private static final String TOTAL_DATA_FILES = ROOT + ".total.data_files.count";
  private static final String ADDED_DELETE_FILES = ROOT + ".added.delete_files.count";
  private static final String ADDED_EQ_DELETE_FILES = ROOT + ".added.equality_delete_files.count";
  private static final String ADDED_POS_DELETE_FILES = ROOT + ".added.position_delete_files.count";
  private static final String ADDED_DVS = ROOT + ".added.dvs.count";
  private static final String REMOVED_POS_DELETE_FILES =
      ROOT + ".removed.positional_delete_files.count";
  private static final String REMOVED_DVS = ROOT + ".removed.dvs.count";
  private static final String REMOVED_EQ_DELETE_FILES =
      ROOT + ".removed.equality_delete_files.count";
  private static final String REMOVED_DELETE_FILES = ROOT + ".removed.delete_files.count";
  private static final String TOTAL_DELETE_FILES = ROOT + ".total.delete_files.count";
  private static final String ADDED_RECORDS = ROOT + ".added.records.count";
  private static final String REMOVED_RECORDS = ROOT + ".removed.records.count";
  private static final String TOTAL_RECORDS = ROOT + ".total.records.count";
  private static final String ADDED_FILE_SIZE_BYTES = ROOT + ".added.files.size";
  private static final String REMOVED_FILE_SIZE_BYTES = ROOT + ".removed.files.size";
  private static final String TOTAL_FILE_SIZE_BYTES = ROOT + ".total.files.size";
  private static final String ADDED_POS_DELETES = ROOT + ".added.position_deletes.count";
  private static final String REMOVED_POS_DELETES = ROOT + ".removed.position_deletes.count";
  private static final String TOTAL_POS_DELETES = ROOT + ".total.position_deletes.count";
  private static final String ADDED_EQ_DELETES = ROOT + ".added.equality_deletes.count";
  private static final String REMOVED_EQ_DELETES = ROOT + ".removed.equality_deletes.count";
  private static final String TOTAL_EQ_DELETES = ROOT + ".total.equality_deletes.count";

  private CommitMetricsBuilder() {
    // prevents instantiation
  }

  static LongGauge duration(Meter meter) {
    return meter
        .gaugeBuilder(DURATION)
        .setDescription("The duration taken to process the commit.")
        .setUnit("ms")
        .ofLongs()
        .build();
  }

  static LongCounter attempts(Meter meter) {
    return meter
        .counterBuilder(ATTEMPTS)
        .setDescription("The number of attempts made to complete this commit.")
        .setUnit("{attempt}")
        .build();
  }

  static LongCounter addedDataFiles(Meter meter) {
    return meter
        .counterBuilder(ADDED_DATA_FILES)
        .setDescription("The number of data files added as part of the commit.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter removedDataFiles(Meter meter) {
    return meter
        .counterBuilder(REMOVED_DATA_FILES)
        .setDescription("The number of data files removed as part of the commit.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter totalDataFiles(Meter meter) {
    return meter
        .counterBuilder(TOTAL_DATA_FILES)
        .setDescription("The number of data files added or removed as part of the commit.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter addedDeleteFiles(Meter meter) {
    return meter
        .counterBuilder(ADDED_DELETE_FILES)
        .setDescription("The overall number of delete files added as part of the commit.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter addedEqualityDeleteFiles(Meter meter) {
    return meter
        .counterBuilder(ADDED_EQ_DELETE_FILES)
        .setDescription("The number of equality delete files added as part of the commit.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter addedPositionDeleteFiles(Meter meter) {
    return meter
        .counterBuilder(ADDED_POS_DELETE_FILES)
        .setDescription("The number of position delete files added as part of the commit.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter addedDeletionVectors(Meter meter) {
    return meter
        .counterBuilder(ADDED_DVS)
        .setDescription("The number of deletion vector files added as part of the commit.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter removedPositionDeleteFiles(Meter meter) {
    return meter
        .counterBuilder(REMOVED_POS_DELETE_FILES)
        .setDescription("The number of position delete files removed as part of the commit.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter removedDeletionVectors(Meter meter) {
    return meter
        .counterBuilder(REMOVED_DVS)
        .setDescription("The number of deletion vector files removed as part of the commit.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter removedEqualityDeleteFiles(Meter meter) {
    return meter
        .counterBuilder(REMOVED_EQ_DELETE_FILES)
        .setDescription("The number of equality delete files removed as part of the commit.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter removedDeleteFiles(Meter meter) {
    return meter
        .counterBuilder(REMOVED_DELETE_FILES)
        .setDescription("The overall number of delete files removed as part of the commit.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter totalDeleteFiles(Meter meter) {
    return meter
        .counterBuilder(TOTAL_DELETE_FILES)
        .setDescription(
            "The overall number of delete files added or removed as part of the commit.")
        .setUnit("{file}")
        .build();
  }

  static LongCounter addedRecords(Meter meter) {
    return meter
        .counterBuilder(ADDED_RECORDS)
        .setDescription("The number of records added as part of the commit.")
        .setUnit("{record}")
        .build();
  }

  static LongCounter removedRecords(Meter meter) {
    return meter
        .counterBuilder(REMOVED_RECORDS)
        .setDescription("The number of records removed as part of the commit.")
        .setUnit("{record}")
        .build();
  }

  static LongCounter totalRecords(Meter meter) {
    return meter
        .counterBuilder(TOTAL_RECORDS)
        .setDescription("The overall number of records added or removed as part of the commit.")
        .setUnit("{record}")
        .build();
  }

  static LongCounter addedFilesSize(Meter meter) {
    return meter
        .counterBuilder(ADDED_FILE_SIZE_BYTES)
        .setDescription(
            "The overall size of the data and delete files added as part of the commit.")
        .setUnit("byte")
        .build();
  }

  static LongCounter removedFilesSize(Meter meter) {
    return meter
        .counterBuilder(REMOVED_FILE_SIZE_BYTES)
        .setDescription(
            "The overall size of the data or delete files removed as part of the commit.")
        .setUnit("byte")
        .build();
  }

  static LongCounter totalFilesSize(Meter meter) {
    return meter
        .counterBuilder(TOTAL_FILE_SIZE_BYTES)
        .setDescription(
            "The overall size of the data or delete files added or removed as part of the commit.")
        .setUnit("byte")
        .build();
  }

  static LongCounter addedPositionDeletes(Meter meter) {
    return meter
        .counterBuilder(ADDED_POS_DELETES)
        .setDescription(
            "The overall number of position delete entries added as part of the commit.")
        .setUnit("{record}")
        .build();
  }

  static LongCounter removedPositionDeletes(Meter meter) {
    return meter
        .counterBuilder(REMOVED_POS_DELETES)
        .setDescription(
            "The overall number of position delete entries removed as part of the commit.")
        .setUnit("{record}")
        .build();
  }

  static LongCounter totalPositionDeletes(Meter meter) {
    return meter
        .counterBuilder(TOTAL_POS_DELETES)
        .setDescription(
            "The overall number of position delete entries added or removed as part of the commit.")
        .setUnit("{record}")
        .build();
  }

  static LongCounter addedEqualityDeletes(Meter meter) {
    return meter
        .counterBuilder(ADDED_EQ_DELETES)
        .setDescription(
            "The overall number of equality delete entries added as part of the commit.")
        .setUnit("{record}")
        .build();
  }

  static LongCounter removedEqualityDeletes(Meter meter) {
    return meter
        .counterBuilder(REMOVED_EQ_DELETES)
        .setDescription(
            "The overall number of equality delete entries removed as part of the commit.")
        .setUnit("{record}")
        .build();
  }

  static LongCounter totalEqualityDeletes(Meter meter) {
    return meter
        .counterBuilder(TOTAL_EQ_DELETES)
        .setDescription(
            "The overall number of equality delete entries added or removed as part of the commit.")
        .setUnit("{record}")
        .build();
  }
}
