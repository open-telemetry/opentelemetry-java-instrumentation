/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.annotations;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

class OpenTelemetryAnnotationsRuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
    hints
        .reflection()
        .registerType(
            TypeReference.of(
                "io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.annotations.InstrumentationWithSpanAspect"),
            hint -> hint.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS))
        .registerType(
            TypeReference.of(
                "io.opentelemetry.instrumentation.spring.autoconfigure.internal.instrumentation.scheduling.SpringSchedulingInstrumentationAspect"),
            hint -> hint.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS));
  }
}
