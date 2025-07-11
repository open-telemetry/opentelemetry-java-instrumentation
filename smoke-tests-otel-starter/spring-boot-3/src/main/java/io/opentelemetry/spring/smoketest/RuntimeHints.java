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
    hints.resources().registerResourceBundle("org.apache.commons.dbcp2.LocalStrings");

    // To avoid Spring native issue with MongoDB: java.lang.ClassNotFoundException:
    // org.springframework.data.mongodb.core.aggregation.AggregationOperation
    hints
        .reflection()
        .registerType(
            TypeReference.of(
                "org.springframework.data.mongodb.core.aggregation.AggregationOperation"),
            hint -> {
              hint.withMembers(MemberCategory.INVOKE_DECLARED_CONSTRUCTORS);
            });
  }
}
