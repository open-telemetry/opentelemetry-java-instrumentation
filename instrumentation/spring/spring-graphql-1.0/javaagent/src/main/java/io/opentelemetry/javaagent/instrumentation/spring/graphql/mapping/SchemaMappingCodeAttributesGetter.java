/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.graphql.mapping;

import io.opentelemetry.instrumentation.api.instrumenter.code.CodeAttributesGetter;

public class SchemaMappingCodeAttributesGetter implements CodeAttributesGetter<SchemaMappingRequest> {

  @Override
  public Class<?> codeClass(SchemaMappingRequest request) {
    return request.getCodeClass();
  }

  @Override
  public String methodName(SchemaMappingRequest request) {
    return request.getMethodName();
  }

}
