/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.common.client.internal

import io.opentelemetry.instrumentation.ktor.v2_0.common.client.AbstractKtorClientTelemetryBuilder

class Experimental private constructor() {

  companion object {
    fun emitExperimentalTelemetry(builder: AbstractKtorClientTelemetryBuilder) {
      builder.internalBuilder.setEmitExperimentalHttpClientMetrics(true)
    }
  }
}
