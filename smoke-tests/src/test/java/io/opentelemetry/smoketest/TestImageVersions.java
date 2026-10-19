/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

// TODO configure renovate to update these versions
public class TestImageVersions {

  // smoke-test-spring-boot
  public static final String SPRING_BOOT_VERSION = "20261007.37549989900";

  // smoke-test-grpc
  public static final String GRPC_VERSION = "20261007.37549990052";

  // smoke-test-play
  public static final String PLAY_VERSION = "20261007.37549989769";

  // smoke-test-quarkus
  public static final String QUARKUS_VERSION = "20261007.37549989866";

  // smoke-test-security-manager
  public static final String SECURITY_MANAGER_VERSION = "20260825.32803070890";

  // smoke-test-zulu-openjdk-8u31
  public static final String ZULU_OPENJDK_8U31_VERSION = "20260825.32803070904";

  // smoke-test-servlet-* (all servlet variants)
  public static final String SERVLET_VERSION = "20261007.37549989456";

  private TestImageVersions() {}
}
