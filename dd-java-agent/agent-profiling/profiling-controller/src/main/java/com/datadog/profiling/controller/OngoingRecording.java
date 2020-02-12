package com.datadog.profiling.controller;

import java.io.Closeable;
import java.time.Instant;

/** Interface that represents ongoing recording in profiling system */
public interface OngoingRecording extends Closeable {

  /**
   * Stop recording.
   *
   * @return {@link RecordingData} with current recording information
   */
  RecordingData stop();

  /**
   * Create snapshot from running recording. Note: recording continues to run after this method is
   * called.
   *
   * @param start start time of the snapshot
   * @param end end time of the snapshot
   * @return {@link RecordingData} with snapshot information
   */
  RecordingData snapshot(final Instant start, final Instant end);

  /** Close recording without capturing any data */
  @Override
  void close();
}
