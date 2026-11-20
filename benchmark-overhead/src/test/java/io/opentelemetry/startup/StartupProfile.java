/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import jdk.jfr.consumer.RecordingFile;

final class StartupProfile {
  static void summarize(Path recording, Instant cutoff, Path output) throws IOException {
    Map<String, Long> inclusive = new HashMap<>();
    Map<String, Long> leaves = new HashMap<>();
    long samples = 0;
    try (RecordingFile file = new RecordingFile(recording)) {
      while (file.hasMoreEvents()) {
        var event = file.readEvent();
        if (!event.getEventType().getName().equals("jdk.ExecutionSample")
            || event.getStartTime().isAfter(cutoff)
            || event.getStackTrace() == null) {
          continue;
        }
        samples++;
        var seen = new HashSet<String>();
        var frames = event.getStackTrace().getFrames();
        for (int i = 0; i < frames.size(); i++) {
          var method = frames.get(i).getMethod();
          String name = method.getType().getName() + "." + method.getName();
          if (seen.add(name)) {
            inclusive.merge(name, 1L, Long::sum);
          }
          if (i == 0) {
            leaves.merge(name, 1L, Long::sum);
          }
        }
      }
    }
    StringBuilder result =
        new StringBuilder("Execution samples before HTTP readiness: " + samples + "\n");
    append(result, "Inclusive frames", inclusive);
    append(result, "Leaf frames", leaves);
    Files.writeString(output, result, UTF_8);
  }

  private static void append(StringBuilder result, String title, Map<String, Long> counts) {
    result.append("\n").append(title).append("\n");
    counts.entrySet().stream()
        .sorted(
            Map.Entry.<String, Long>comparingByValue()
                .reversed()
                .thenComparing(Map.Entry.comparingByKey()))
        .limit(100)
        .forEach(
            entry ->
                result.append(entry.getValue()).append(" ").append(entry.getKey()).append("\n"));
  }

  private StartupProfile() {}
}
