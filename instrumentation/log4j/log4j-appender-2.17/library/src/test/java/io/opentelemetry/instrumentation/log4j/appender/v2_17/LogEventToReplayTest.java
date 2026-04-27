/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.log4j.appender.v2_17;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.ParameterizedMessage;
import org.junit.jupiter.api.Test;

class LogEventToReplayTest {

  @Test
  void copiesMessageParametersDefensively() {
    LogEventToReplay logEventToReplay =
        new LogEventToReplay(
            Log4jLogEvent.newBuilder()
                .setMessage(new ParameterizedMessage("hello {}", "world"))
                .build(),
            false);

    Object[] parameters = logEventToReplay.getMessage().getParameters();
    parameters[0] = "mutated";

    assertThat(logEventToReplay.getMessage().getParameters()).containsExactly("world");
  }
}
