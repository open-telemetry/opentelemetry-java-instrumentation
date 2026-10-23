/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.spring.smoketest;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHintsRegistrar;
import org.springframework.aot.hint.TypeReference;

// Necessary for GraalVM native test
public class RuntimeHints implements RuntimeHintsRegistrar {

  @Override
  public void registerHints(
      org.springframework.aot.hint.RuntimeHints hints, ClassLoader classLoader) {
    hints
        .reflection()
        .registerType(
            TypeReference.of("org.apache.coyote.AbstractProtocol"),
            hint -> hint.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS));
    hints
        .reflection()
        .registerType(
            TypeReference.of("org.apache.coyote.http11.AbstractHttp11Protocol"),
            hint -> hint.withMembers(MemberCategory.INVOKE_PUBLIC_METHODS));
  }
}
