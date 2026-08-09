/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.docs.utils;

import io.opentelemetry.instrumentation.docs.internal.InstrumentationType;

/**
 * A discovered instrumentation source directory.
 *
 * @param srcPath repository-relative module path, using forward slashes on every platform
 */
public record InstrumentationPath(
    String instrumentationName,
    String srcPath,
    String namespace,
    String group,
    InstrumentationType type) {}
