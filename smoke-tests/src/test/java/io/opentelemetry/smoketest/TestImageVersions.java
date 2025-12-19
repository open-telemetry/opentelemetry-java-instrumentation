/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

// TODO configure renovate to update these versions
public final class TestImageVersions {

  // smoke-test-spring-boot
  public static final String SPRING_BOOT_VERSION = "20251116.19402383847";

  // smoke-test-grpc
  public static final String GRPC_VERSION = "20251117.19445937433";

  // smoke-test-play
  public static final String PLAY_VERSION = "20251117.19437482782";

  // smoke-test-quarkus
  public static final String QUARKUS_VERSION = "20251119.19511997816";

  // smoke-test-security-manager
  public static final String SECURITY_MANAGER_VERSION = "20251116.19402383852";

  // smoke-test-zulu-openjdk-8u31
  public static final String ZULU_OPENJDK_8U31_VERSION = "20251117.19421579350";

  // smoke-test-servlet-* (all servlet variants)
  public static final String SERVLET_VERSION = "20251120.19538040041";

  private TestImageVersions() {}
}
