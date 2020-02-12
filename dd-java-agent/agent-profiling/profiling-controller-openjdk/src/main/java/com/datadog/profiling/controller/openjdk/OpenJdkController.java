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

import com.datadog.profiling.controller.ConfigurationException;
import com.datadog.profiling.controller.Controller;
import datadog.trace.api.Config;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import jdk.jfr.Recording;

/**
 * This is the implementation of the controller for OpenJDK. It should work for JDK 11+ today, and
 * unmodified for JDK 8+ once JFR has been back-ported. The Oracle JDK implementation will be far
 * messier... ;)
 */
public final class OpenJdkController implements Controller {
  // Visible for testing
  static final String JFP = "jfr/dd.jfp";
  static final int RECORDING_MAX_SIZE = 64 * 1024 * 1024; // 64 megs
  static final Duration RECORDING_MAX_AGE = Duration.ofMinutes(5);

  private final Map<String, String> recordingSettings;

  /**
   * Main constructor for OpenJDK profiling controller.
   *
   * <p>This has to be public because it is created via reflection
   */
  public OpenJdkController(final Config config)
      throws ConfigurationException, ClassNotFoundException {
    // Make sure we can load JFR classes before declaring that we have successfully created
    // factory and can use it.
    Class.forName("jdk.jfr.Recording");
    Class.forName("jdk.jfr.FlightRecorder");

    try {
      recordingSettings =
          JfpUtils.readNamedJfpResource(JFP, config.getProfilingTemplateOverrideFile());
    } catch (final IOException e) {
      throw new ConfigurationException(e);
    }
  }

  @Override
  public OpenJdkOngoingRecording createRecording(final String recordingName) {
    final Recording recording = new Recording();
    recording.setName(recordingName);
    recording.setSettings(recordingSettings);
    recording.setMaxSize(RECORDING_MAX_SIZE);
    recording.setMaxAge(RECORDING_MAX_AGE);
    recording.start();
    return new OpenJdkOngoingRecording(recording);
  }
}
