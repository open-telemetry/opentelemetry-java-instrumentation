/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.common.v2_0.internal

import io.opentelemetry.instrumentation.ktor.common.v2_0.AbstractKtorClientTelemetryBuilder
import io.opentelemetry.instrumentation.ktor.common.v2_0.AbstractKtorServerTelemetryBuilder

class Experimental private constructor() {

  companion object {
    fun emitExperimentalTelemetry(builder: AbstractKtorClientTelemetryBuilder) {
      builder.builder().setEmitExperimentalHttpClientTelemetry(true)
    }

    fun emitExperimentalTelemetry(builder: AbstractKtorServerTelemetryBuilder) {
      builder.builder.setEmitExperimentalHttpServerTelemetry(true)
    }
  }
}
