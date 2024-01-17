/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mybatis;

import io.opentelemetry.api.common.AttributesBuilder;
import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.instrumenter.AttributesExtractor;
import javax.annotation.Nullable;

class MybatisAttributesExtractor implements AttributesExtractor<MapperMethodRequest, Void> {

  @Override
  public void onStart(
      AttributesBuilder attributes,
      Context parentContext,
      MapperMethodRequest mapperMethodRequest) {
    attributes.put("component.name", "mybatis");
  }

  @Override
  public void onEnd(
      AttributesBuilder attributes,
      Context context,
      MapperMethodRequest mapperMethodRequest,
      @Nullable Void unused,
      @Nullable Throwable error) {}
}
