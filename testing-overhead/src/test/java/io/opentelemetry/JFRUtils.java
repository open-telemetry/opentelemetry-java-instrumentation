/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package io.opentelemetry;

import java.io.IOException;
import java.nio.file.Path;
import java.util.function.BiFunction;
import io.opentelemetry.AppPerfResults.MinMax;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

public class JFRUtils {

  static long totalLongEvents(Path jfrFile, String eventName,
      String longValueKey) throws IOException {
    return reduce(jfrFile, eventName, longValueKey, 0L, Long::sum);
  }

  static float findMaxFloat(Path jfrFile, String eventName, String valueKey) throws IOException {
    return reduce(jfrFile, eventName, valueKey, 0.0f, (BiFunction<Float, Float, Float>) Math::max);
  }

  static MinMax findMinMax(Path jfrFile, String eventName, String valueKey) throws IOException {
    return reduce(jfrFile, eventName, valueKey, new MinMax(), (MinMax acc, Long value) -> {
      if (value > acc.max) {
          acc.max = value;
        }
        if (value < acc.min) {
          acc.min = value;
        }
        return acc;
    });
  }

  private static <T, V> T reduce(Path jfrFile, String eventName,
      String valueKey, T initial, BiFunction<T,V,T> reducer) throws IOException {
    RecordingFile recordingFile = new RecordingFile(jfrFile);
    T result = initial;
    while (recordingFile.hasMoreEvents()) {
      RecordedEvent recordedEvent = recordingFile.readEvent();
      if (eventName.equals(recordedEvent.getEventType().getName())) {
        V value = recordedEvent.getValue(valueKey);
        result = reducer.apply(result, value);
      }
    }
    return result;
  }
}
