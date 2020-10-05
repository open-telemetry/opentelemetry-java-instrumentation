/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Class used for exception handler logging.
 *
 * <p>See io.opentelemetry.javaagent.tooling.ExceptionHandlers
 */
public class ExceptionLogger {
  public static final Logger LOGGER = LoggerFactory.getLogger(ExceptionLogger.class);
}
