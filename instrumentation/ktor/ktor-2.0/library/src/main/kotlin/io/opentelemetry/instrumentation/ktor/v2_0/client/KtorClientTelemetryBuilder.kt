/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.v2_0.client

import io.opentelemetry.instrumentation.ktor.client.AbstractKtorClientTelemetryBuilder
import io.opentelemetry.instrumentation.ktor.v2_0.InstrumentationProperties.INSTRUMENTATION_NAME

class KtorClientTelemetryBuilder : AbstractKtorClientTelemetryBuilder(INSTRUMENTATION_NAME) {

  internal fun build(): KtorClientTelemetry = KtorClientTelemetry(
    instrumenter = clientBuilder.build(),
    propagators = getOpenTelemetry().propagators,
  )
}
