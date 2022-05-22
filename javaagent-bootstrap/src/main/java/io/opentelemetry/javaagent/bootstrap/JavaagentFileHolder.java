/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.io.File;
import javax.annotation.Nullable;

// this is currently unused in this repository, but is available for use in distros
/** This class serves as an "everywhere accessible" source of the agent jar file. */
public class JavaagentFileHolder {

  @Nullable private static volatile File javaagentFile;

  @Nullable
  public static File getJavaagentFile() {
    return javaagentFile;
  }

  public static void setJavaagentFile(File javaagentFile) {
    JavaagentFileHolder.javaagentFile = javaagentFile;
  }
}
