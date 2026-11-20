/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.closedmodule

import io.opentelemetry.instrumentation.annotations.Counted
import kotlinx.coroutines.CompletableDeferred

class ClosedModuleCountedMethods {
  @Counted("closed.module.count")
  suspend fun suspending(gate: CompletableDeferred<Unit>): Int {
    gate.await()
    return 42
  }
}
