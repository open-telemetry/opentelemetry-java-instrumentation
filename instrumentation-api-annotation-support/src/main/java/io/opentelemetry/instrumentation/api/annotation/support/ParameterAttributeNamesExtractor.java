/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.api.annotation.support;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import org.checkerframework.checker.nullness.qual.Nullable;

@FunctionalInterface
public interface ParameterAttributeNamesExtractor {
  @Nullable
  String[] extract(Method method, Parameter[] parameters);
}
