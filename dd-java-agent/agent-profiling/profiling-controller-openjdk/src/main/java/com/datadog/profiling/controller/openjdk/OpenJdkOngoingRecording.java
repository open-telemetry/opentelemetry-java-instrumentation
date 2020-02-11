package com.datadog.profiling.controller.openjdk;

import com.datadog.profiling.controller.OngoingRecording;
import java.time.Instant;
import jdk.jfr.FlightRecorder;
import jdk.jfr.Recording;
import jdk.jfr.RecordingState;

public class OpenJdkOngoingRecording implements OngoingRecording {

  private final Recording recording;

  OpenJdkOngoingRecording(final Recording recording) {
    this.recording = recording;
  }

  @Override
  public OpenJdkRecordingData stop() {
    if (recording.getState() != RecordingState.RUNNING) {
      throw new IllegalStateException("Cannot stop recording that is not running");
    }

    recording.stop();
    return new OpenJdkRecordingData(recording);
  }

  @Override
  public OpenJdkRecordingData snapshot(final Instant start, final Instant end) {
    if (recording.getState() != RecordingState.RUNNING) {
      throw new IllegalStateException("Cannot snapshot recording that is not running");
    }

    final Recording snapshot = FlightRecorder.getFlightRecorder().takeSnapshot();
    snapshot.setName(recording.getName()); // Copy name from original recording
    return new OpenJdkRecordingData(snapshot, start, end);
  }

  @Override
  public void close() {
    recording.close();
  }
}
