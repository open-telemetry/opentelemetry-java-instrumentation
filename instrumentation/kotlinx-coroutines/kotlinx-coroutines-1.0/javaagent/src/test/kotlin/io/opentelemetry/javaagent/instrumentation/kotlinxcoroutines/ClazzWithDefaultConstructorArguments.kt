/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines

import io.opentelemetry.instrumentation.annotations.WithSpan
import kotlinx.coroutines.delay

class ClazzWithDefaultConstructorArguments(val name: String = "Ktor") {

  @WithSpan
  suspend fun sayHello(): String {
    delay(10)
    return "Hello World $name from ${ClazzWithDefaultConstructorArguments::class.simpleName}!"
  }
}
