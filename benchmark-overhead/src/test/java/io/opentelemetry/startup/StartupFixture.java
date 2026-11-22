/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.startup;

final class StartupFixture {
  static final String IMAGE =
      "ghcr.io/open-telemetry/opentelemetry-java-instrumentation/"
          + "smoke-test-spring-boot:jdk25-20251116.19402383847";
  static final String APPLICATION = "io.opentelemetry.smoketest.springboot.SpringbootApplication";

  private StartupFixture() {}
}
