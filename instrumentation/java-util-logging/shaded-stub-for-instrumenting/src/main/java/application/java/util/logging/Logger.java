/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package application.java.util.logging;

import java.util.logging.Level;

// java.util.logging.Logger shaded so that it can be used in instrumentation
// of java.util.logging.Logger itself, and then its usage can be unshaded
// after java.util.logging.Logger is shaded to the "PatchLogger"
public class Logger {

  public boolean isLoggable(@SuppressWarnings("unused") Level level) {
    throw new UnsupportedOperationException();
  }
}
