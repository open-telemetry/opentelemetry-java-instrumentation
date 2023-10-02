/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal.injection;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public enum InjectionMode {
  CLASS_ONLY
  // TODO: implement the modes RESOURCE_ONLY and CLASS_AND_RESOURCE
  // This will require a custom URL implementation for byte arrays, similar to how bytebuddy's
  // ByteArrayClassLoader does it

}
