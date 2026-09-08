/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.smoketest;

// TODO configure renovate to update these versions
public class TestImageVersions {

  // smoke-test-spring-boot
  public static final String SPRING_BOOT_VERSION = "20260826.32962641676";

  // Last exploded image, used on JDK 8 and 11 because bootJar launchers require JDK 17
  public static final String SPRING_BOOT_JDK_8_11_VERSION = "20260331.23783210915";

  // smoke-test-grpc
  public static final String GRPC_VERSION = "20260908.34190084723";

  // smoke-test-play
  public static final String PLAY_VERSION = "20260825.32803070850";

  // smoke-test-quarkus
  public static final String QUARKUS_VERSION = "20260908.34191901987";

  // smoke-test-security-manager
  public static final String SECURITY_MANAGER_VERSION = "20260825.32803070890";

  // smoke-test-zulu-openjdk-8u31
  public static final String ZULU_OPENJDK_8U31_VERSION = "20260825.32803070904";

  // smoke-test-servlet-* (all servlet variants)
  public static final String SERVLET_VERSION = "20260825.32803070686";

  private TestImageVersions() {}
}
