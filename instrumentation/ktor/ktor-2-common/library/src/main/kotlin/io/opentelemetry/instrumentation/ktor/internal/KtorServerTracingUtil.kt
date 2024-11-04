/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.internal

import io.ktor.server.request.*
import io.ktor.server.response.*
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter
import io.opentelemetry.instrumentation.api.instrumenter.SpanKindExtractor
import io.opentelemetry.instrumentation.api.internal.InstrumenterUtil
import io.opentelemetry.instrumentation.ktor.server.AbstractKtorServerTracing
import io.opentelemetry.instrumentation.ktor.server.ApplicationRequestGetter

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
object KtorServerTracingUtil {

  fun instrumenter(configuration: AbstractKtorServerTracing.Configuration): Instrumenter<ApplicationRequest, ApplicationResponse> {
    return InstrumenterUtil.buildUpstreamInstrumenter(
      configuration.serverBuilder.instrumenterBuilder(),
      ApplicationRequestGetter,
      configuration.spanKindExtractor(SpanKindExtractor.alwaysServer())
    )
  }
}
