/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.windows;

import org.slf4j.Logger;

public class Slf4jDockerLogLineListener implements ContainerLogHandler.Listener {
  private final Logger logger;

  public Slf4jDockerLogLineListener(Logger logger) {
    this.logger = logger;
  }

  @Override
  public void accept(ContainerLogHandler.LineType type, String text) {
    String normalizedText = text.replaceAll("((\\r?\\n)|(\\r))$", "");

    switch (type) {
      case STDERR:
        this.logger.error("STDERR: {}", normalizedText);
        break;
      case STDOUT:
        this.logger.info("STDOUT: {}", normalizedText);
        break;
    }
  }
}
