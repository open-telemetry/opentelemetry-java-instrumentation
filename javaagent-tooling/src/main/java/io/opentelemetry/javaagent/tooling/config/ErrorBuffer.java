/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.config;

import static java.util.logging.Level.SEVERE;

import java.util.logging.Logger;
import javax.annotation.Nullable;

/**
 * This class is used to buffer error messages that occur during early initialization of the agent.
 * It allows for logging these errors later, once the logging subsystem is fully initialized.
 */
public final class ErrorBuffer {

  // this class is used early, and must not use logging in most of its methods
  // in case any file loading/parsing error occurs, we save the error message and log it later, when
  // the logging subsystem is initialized
  @Nullable private static String errorMessage;

  static void setErrorMessage(String errorMessage) {
    if (ErrorBuffer.errorMessage == null) {
      ErrorBuffer.errorMessage = errorMessage;
    } else {
      ErrorBuffer.errorMessage += "\n" + errorMessage;
    }
  }

  public static void logErrorIfAny() {
    if (errorMessage != null) {
      Logger.getLogger(ConfigurationPropertiesSupplier.class.getName()).log(SEVERE, errorMessage);
    }
  }
}
