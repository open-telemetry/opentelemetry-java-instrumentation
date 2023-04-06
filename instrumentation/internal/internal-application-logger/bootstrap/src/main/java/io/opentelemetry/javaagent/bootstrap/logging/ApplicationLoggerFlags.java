/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.logging;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ApplicationLoggerFlags {

  // by default, the instrumentation aims to instrument the slf4j LoggerFactory; unless the presence
  // of the SpringApplication class is detected, in which case we disable the LoggerFactory
  // instrumentation and instead enable the Spring Boot's LoggingApplicationListener instrumentation
  private static final AtomicBoolean bridgeLoggerFactory = new AtomicBoolean(true);
  private static final AtomicBoolean bridgeSpringBootLogging = new AtomicBoolean(false);

  private ApplicationLoggerFlags() {}

  /**
   * Disables the sfl4j {@code LoggerFactory} instrumentation; and instead enables the Spring Boot
   * {@code LoggingApplicationListener} instrumentation. In Spring Boot, an implementation of {@code
   * LoggingApplicationListener} is responsible for initializing the logging library. Even though
   * slf4j (and its {@code LoggerFactory}) is actually logged earlier, it is not usable until the
   * {@code LoggingApplicationListener} finishes its initialization process.
   */
  public static void setSpringBootApp() {
    bridgeLoggerFactory.set(false);
    bridgeSpringBootLogging.set(true);
  }

  /**
   * Return true when the {@code LoggerFactory} instrumentation is allowed to install an application
   * logger bridge. Will return true at most once.
   */
  public static boolean bridgeLoggerFactory() {
    return bridgeLoggerFactory.compareAndSet(true, false);
  }

  /**
   * Return true when the {@code LoggingApplicationListener} instrumentation is allowed to install
   * an application logger bridge. Will return true at most once.
   */
  public static boolean bridgeSpringBootLogging() {
    return bridgeSpringBootLogging.compareAndSet(true, false);
  }
}
