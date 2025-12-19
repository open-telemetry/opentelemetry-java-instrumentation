/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import javax.annotation.Nullable;

class ElasticJobCodeAttributesGetter implements CodeAttributesGetter<ElasticJobProcessRequest> {
  @Nullable
  @Override
  public Class<?> getCodeClass(ElasticJobProcessRequest request) {
    return request.getUserJobClass();
  }

  @Nullable
  @Override
  public String getMethodName(ElasticJobProcessRequest request) {
    return request.getUserMethodName();
  }
}
