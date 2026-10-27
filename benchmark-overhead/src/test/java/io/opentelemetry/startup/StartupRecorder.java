/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import java.nio.file.Path;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;

public final class StartupRecorder {
  private static Recording recording;

  public static void premain(String destination) throws Exception {
    recording = new Recording(Configuration.getConfiguration("profile"));
    recording.setName("startup");
    recording.setDestination(Path.of(destination));
    recording.setDumpOnExit(true);
    recording.start();
    System.out.println("Startup recording active before telemetry agent");
  }

  private StartupRecorder() {}
}
