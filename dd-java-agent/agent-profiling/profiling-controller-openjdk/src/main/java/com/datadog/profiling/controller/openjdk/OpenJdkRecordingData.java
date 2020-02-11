/*
 * Copyright 2019 Datadog
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.datadog.profiling.controller.openjdk;

import com.datadog.profiling.controller.RecordingData;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import jdk.jfr.Recording;

/** Implementation for profiling recordings. */
public class OpenJdkRecordingData implements RecordingData {

  private final Recording recording;
  private final Instant start;
  private final Instant end;

  OpenJdkRecordingData(final Recording recording) {
    this(recording, recording.getStartTime(), recording.getStopTime());
  }

  OpenJdkRecordingData(final Recording recording, final Instant start, final Instant end) {
    this.recording = recording;
    this.start = start;
    this.end = end;
  }

  @Override
  public InputStream getStream() throws IOException {
    return recording.getStream(start, end);
  }

  @Override
  public void release() {
    recording.close();
  }

  @Override
  public String getName() {
    return recording.getName();
  }

  @Override
  public String toString() {
    return "OpenJdkRecording: " + getName();
  }

  @Override
  public Instant getStart() {
    return start;
  }

  @Override
  public Instant getEnd() {
    return end;
  }

  // Visible for testing
  Recording getRecording() {
    return recording;
  }
}
