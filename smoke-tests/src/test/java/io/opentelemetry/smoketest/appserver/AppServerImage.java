/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

class AppServerImage {
  private final String jdk;
  private final String serverVersion;
  private final boolean windows;

  public AppServerImage(String jdk, String serverVersion, boolean windows) {
    this.jdk = jdk;
    this.serverVersion = serverVersion;
    this.windows = windows;
  }

  public String getJdk() {
    return jdk;
  }

  public String getServerVersion() {
    return serverVersion;
  }

  public boolean isWindows() {
    return windows;
  }
}
