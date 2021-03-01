/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling;

/**
 * Some useful constants.
 *
 * <p>Idea here is to keep this class safe to inject into client's class loader.
 */
public final class Constants {

  /** packages which will be loaded on the bootstrap classloader. */
  public static final String[] BOOTSTRAP_PACKAGE_PREFIXES = {
    "io.opentelemetry.javaagent.common.exec",
    "io.opentelemetry.javaagent.slf4j",
    "io.opentelemetry.javaagent.bootstrap",
    "io.opentelemetry.javaagent.shaded",
    "io.opentelemetry.javaagent.instrumentation.api",
  };

  // This is used in IntegrationTestUtils.java
  public static final String[] AGENT_PACKAGE_PREFIXES = {
    "io.opentelemetry.instrumentation.api",
    // guava
    "com.google.auto",
    "com.google.common",
    "com.google.thirdparty.publicsuffix",
    // WeakConcurrentMap
    "com.blogspot.mydailyjava.weaklockfree",
    // bytebuddy
    "net.bytebuddy",
    "org.yaml.snakeyaml",
    // disruptor
    "com.lmax.disruptor",
    // okHttp
    "okhttp3",
    "okio",
    "jnr",
    "org.objectweb.asm",
    "com.kenai",
    // Custom RxJava Utility
    "rx.__OpenTelemetryTracingUtil",
  };

  private Constants() {}
}
