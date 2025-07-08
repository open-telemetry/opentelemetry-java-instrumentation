/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.util.logging.Level.SEVERE;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * This class is used to buffer error messages that occur during early initialization of the agent.
 * It allows for logging these errors later, once the logging subsystem is fully initialized.
 */
public final class ErrorBuffer {

  // this class is used early, and must not use logging in most of its methods
  // in case any file loading/parsing error occurs, we save the error message and log it later, when
  // the logging subsystem is initialized
  private static final List<String> errorMessages = new ArrayList<>();

  static void addErrorMessage(String errorMessage) {
    errorMessages.add(errorMessage);
  }

  public static void logErrorIfAny() {
    for (String message : errorMessages) {
      Logger.getLogger(ConfigurationPropertiesSupplier.class.getName()).log(SEVERE, message);
    }
  }
}
