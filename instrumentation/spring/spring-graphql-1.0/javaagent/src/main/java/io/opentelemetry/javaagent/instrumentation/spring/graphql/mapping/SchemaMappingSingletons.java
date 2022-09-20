/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.graphql.mapping;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.code.CodeSpanNameExtractor;

public class SchemaMappingSingletons {

  private static final String INSTRUMENTATION_NAME = "io.opentelemetry.spring-graphql-1.0";

  private static final Instrumenter<SchemaMappingRequest, Void> INSTRUMENTER;

  static {
    SchemaMappingCodeAttributesGetter codeAttributesGetter = new SchemaMappingCodeAttributesGetter();

    INSTRUMENTER = Instrumenter.<SchemaMappingRequest, Void>builder(
            GlobalOpenTelemetry.get(),
            INSTRUMENTATION_NAME,
            CodeSpanNameExtractor.create(codeAttributesGetter))
        .addAttributesExtractor(CodeAttributesExtractor.create(codeAttributesGetter))
        .buildInstrumenter();
  }

  public static Instrumenter<SchemaMappingRequest, Void> instrumenter() {
    return INSTRUMENTER;
  }

  private SchemaMappingSingletons() {}

}
