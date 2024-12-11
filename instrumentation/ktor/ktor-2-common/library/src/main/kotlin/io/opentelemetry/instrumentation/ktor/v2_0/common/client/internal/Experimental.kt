/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.common.client.internal

import io.opentelemetry.instrumentation.ktor.v2_0.common.client.AbstractKtorClientTelemetryBuilder
import java.util.function.BiConsumer

class Experimental private constructor() {

  companion object {
    @Volatile
    private var setEmitExperimentalTelemetry: BiConsumer<AbstractKtorClientTelemetryBuilder, Boolean>? = null

    fun emitExperimentalTelemetry(builder: AbstractKtorClientTelemetryBuilder) {
      if (setEmitExperimentalTelemetry != null) {
        setEmitExperimentalTelemetry!!.accept(builder, true)
      }
    }

    fun setSetEmitExperimentalTelemetry(setEmitExperimentalTelemetry: BiConsumer<AbstractKtorClientTelemetryBuilder, Boolean>?) {
      Companion.setEmitExperimentalTelemetry = setEmitExperimentalTelemetry
    }
  }
}
