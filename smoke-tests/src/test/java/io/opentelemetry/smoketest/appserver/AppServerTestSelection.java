/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest.appserver;

final class AppServerTestSelection {
  static boolean includes(AppServer appServer, String group, boolean windows) {
    // OpenJ9 images are not published for supported Windows containers.
    if (windows && appServer.jdk().endsWith("-openj9")) {
      return false;
    }
    switch (group) {
      case "all":
        return true;
      case "limitedMatrix":
        return appServer.inLimitedMatrix();
      case "additionalVariants":
        return !appServer.inLimitedMatrix();
      default:
        throw new IllegalArgumentException("Unknown app-server test group: " + group);
    }
  }

  private AppServerTestSelection() {}
}
