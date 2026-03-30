/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.apacheelasticjob.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;

class ElasticJobCodeAttributesGetter implements CodeAttributesGetter<ElasticJobProcessRequest> {
  @Override
  public Class<?> getCodeClass(ElasticJobProcessRequest request) {
    return request.getUserJobClass();
  }

  @Override
  public String getMethodName(ElasticJobProcessRequest request) {
    return request.getUserMethodName();
  }
}
