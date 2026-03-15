/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.logging.application;

import static io.opentelemetry.javaagent.bootstrap.InternalLogger.Level.INFO;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import org.junit.jupiter.api.Test;

class InMemoryLogTest {

  @Test
  void testDump() throws UnsupportedEncodingException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    InMemoryLog log = InMemoryLog.create("test-logger", INFO, "a", new RuntimeException("boom!"));

    log.dump(new PrintStream(out));

    assertThat(out.toString(UTF_8.name()))
        .startsWith(
            "[otel.javaagent] INFO test-logger - a"
                + System.lineSeparator()
                + "java.lang.RuntimeException: boom!");
  }
}
