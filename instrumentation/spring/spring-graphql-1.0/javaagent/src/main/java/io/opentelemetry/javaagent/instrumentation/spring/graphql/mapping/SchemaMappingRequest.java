/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.spring.graphql.mapping;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class SchemaMappingRequest {

  public static SchemaMappingRequest create(Class<?> codeClass, String methodName) {
    return new AutoValue_SchemaMappingRequest(codeClass, methodName);
  }

  public abstract Class<?> getCodeClass();

  public abstract String getMethodName();

}
