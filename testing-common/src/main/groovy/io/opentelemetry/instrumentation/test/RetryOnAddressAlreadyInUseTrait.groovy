/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.test

import org.slf4j.Logger
import org.slf4j.LoggerFactory

/**
 * A trait for retrying operation when it fails with "java.net.BindException: Address already in use"
 */
trait RetryOnAddressAlreadyInUseTrait {
  private static final Logger log = LoggerFactory.getLogger(RetryOnAddressAlreadyInUseTrait)

  /**
   * This is used by setupSpec() methods to auto-retry setup that depends on finding and then using
   * an available free port, because that kind of setup can fail sporadically if the available port
   * gets re-used between when we find the available port and when we use it.
   *
   * @param closure the groovy closure to run with retry
   */
  static void withRetryOnAddressAlreadyInUse(Closure<?> closure) {
    withRetryOnAddressAlreadyInUse(closure, 3)
  }

  static void withRetryOnAddressAlreadyInUse(Closure<?> closure, int numRetries) {
    try {
      closure.call()
    } catch (Throwable t) {
      // typically this is "java.net.BindException: Address already in use", but also can be
      // "io.netty.channel.unix.Errors$NativeIoException: bind() failed: Address already in use"
      if (numRetries == 0 || !t.getMessage().contains("Address already in use")) {
        throw t
      }
      log.debug("retrying due to bind exception: {}", t.getMessage(), t)
      withRetryOnAddressAlreadyInUse(closure, numRetries - 1)
    }
  }
}