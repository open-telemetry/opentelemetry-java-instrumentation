/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.extension.instrumentation.internal;

import org.objectweb.asm.Opcodes;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
public final class AsmApi {
  public static final int VERSION = Opcodes.ASM9;

  private AsmApi() {}
}
