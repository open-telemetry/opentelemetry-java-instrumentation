/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.ktor.client.internal

import io.opentelemetry.instrumentation.ktor.client.AbstractKtorClientTracingBuilder
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.logging.Level
import java.util.logging.Logger

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
// TODO (trask) update the above javadoc similar to
//  https://github.com/open-telemetry/opentelemetry-java/pull/6886
class Experimental {
  fun setEmitExperimentalHttpClientMetrics(builder: AbstractKtorClientTracingBuilder?, emitExperimentalHttpClientMetrics: Boolean) {
    if (emitExperimentalHttpClientMetricsMethod != null) {
      try {
        emitExperimentalHttpClientMetricsMethod.invoke(builder, emitExperimentalHttpClientMetrics)
      } catch (e: IllegalAccessException) {
        logger.log(Level.FINE, e.message, e)
      } catch (e: InvocationTargetException) {
        logger.log(Level.FINE, e.message, e)
      }
    }
  }

  companion object {
    private val logger: Logger = Logger.getLogger(Experimental::class.java.name)

    private val emitExperimentalHttpClientMetricsMethod = getEmitExperimentalHttpClientMetricsMethod()

    fun getEmitExperimentalHttpClientMetricsMethod(): Method? {
      try {
        return AbstractKtorClientTracingBuilder::class.java.getMethod(
          "setEmitExperimentalHttpClientMetrics",
          Boolean::class.javaPrimitiveType
        )
      } catch (e: NoSuchMethodException) {
        logger.log(Level.FINE, e.message, e)
        return null
      }
    }
  }
}
