/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package external.instrumentation;

import external.NotInstrumentation;

public class ExternalHelper {
  public void instrument() {
    new NotInstrumentation().someLibraryCode();
  }
}
