/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.micrometer.v1_5;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UcumUnitsTest {

  @ParameterizedTest
  @CsvSource({
    "bytes, By",
    "seconds, s",
    "threads, {thread}",
    "objects, {object}",
    "sessions, {session}",
    "messages, {message}",
    "tasks, {task}",
    "classes, {class}",
    "events, {event}",
    "files, {file}",
    "buffers, {buffer}",
    "connections, {connection}",
    "operations, {operation}",
    "records, {record}",
    "rows, {row}",
  })
  void mapsKnownUnits(String baseUnit, String expected) {
    assertThat(UcumUnits.normalize(baseUnit)).isEqualTo(expected);
  }

  @Test
  void preservesAmbiguousPercentUnit() {
    assertThat(UcumUnits.normalize("percent")).isEqualTo("percent");
  }

  @ParameterizedTest
  @CsvSource({"widgets", "items", "things", "ms", "ns", "By", "{thread}", "Bytes"})
  void passesThroughUnknownUnits(String baseUnit) {
    assertThat(UcumUnits.normalize(baseUnit)).isEqualTo(baseUnit);
  }

  @Test
  void passesThroughEmptyUnit() {
    assertThat(UcumUnits.normalize("")).isEmpty();
  }
}
