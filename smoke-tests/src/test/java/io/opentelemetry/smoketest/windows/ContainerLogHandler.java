/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.windows;

public interface ContainerLogHandler {
  void addListener(Listener listener);

  interface Listener {
    void accept(LineType type, String text);
  }

  enum LineType {
    STDOUT,
    STDERR
  }
}
