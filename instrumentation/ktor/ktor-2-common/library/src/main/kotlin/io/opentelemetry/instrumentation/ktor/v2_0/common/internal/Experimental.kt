/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.common.internal

import io.opentelemetry.instrumentation.ktor.v2_0.common.AbstractKtorClientTelemetryBuilder
import io.opentelemetry.instrumentation.ktor.v2_0.common.AbstractKtorServerTelemetryBuilder

class Experimental private constructor() {

  companion object {
    fun emitExperimentalTelemetry(builder: AbstractKtorClientTelemetryBuilder) {
      builder.builder().setEmitExperimentalHttpClientMetrics(true)
    }

    fun emitExperimentalTelemetry(builder: AbstractKtorServerTelemetryBuilder) {
      builder.builder.setEmitExperimentalHttpServerMetrics(true)
    }
  }
}
