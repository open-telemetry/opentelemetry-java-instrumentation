/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.logging;

import io.opentelemetry.javaagent.Slf4jLogRecorder;
import io.opentelemetry.javaagent.bootstrap.Slf4jBridgeLogRecorderHolder;

public class Slf4jBridgeInstaller {
  private Slf4jBridgeInstaller() {}

  public static void installSlf4jLogger(Slf4jLogRecorder slf4JLogRecorder) {
    Slf4jBridgeLogRecorderHolder.initialize(slf4JLogRecorder);
  }
}
