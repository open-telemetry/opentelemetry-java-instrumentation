/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

public interface StopWatch extends AutoCloseable {
  void stop();

  void close();
}
