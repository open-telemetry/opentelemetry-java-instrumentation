/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mybatis.v3_2;

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class MapperMethodRequest {

  public static MapperMethodRequest create(String mapperName) {
    return new AutoValue_MapperMethodRequest(mapperName);
  }

  public abstract String getMapperName();
}
