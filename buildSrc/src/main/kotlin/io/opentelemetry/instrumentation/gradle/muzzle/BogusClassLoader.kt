/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.gradle.muzzle

import java.security.SecureClassLoader

internal class BogusClassLoader : SecureClassLoader() {
  override fun toString(): String {
    return "bogus"
  }
}
