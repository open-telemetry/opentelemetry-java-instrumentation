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
    if (request.isScriptJob() || request.isHttpJob()) {
      return null;
    }
    return request.getUserJobClass();
  }

  @Nullable
  @Override
  public String getMethodName(ElasticJobProcessRequest request) {
    if (request.isScriptJob() || request.isHttpJob()) {
      return null;
    }
    return request.getUserMethodName() != null ? request.getUserMethodName() : "process";
  }
}
