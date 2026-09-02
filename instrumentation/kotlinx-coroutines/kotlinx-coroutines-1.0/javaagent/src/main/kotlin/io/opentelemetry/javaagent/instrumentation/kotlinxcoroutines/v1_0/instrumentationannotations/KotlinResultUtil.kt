/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.kotlinxcoroutines.v1_0.instrumentationannotations

@JvmName("exceptionOrNull")
fun resultExceptionOrNull(result: Result<*>): Throwable? = result.exceptionOrNull()
