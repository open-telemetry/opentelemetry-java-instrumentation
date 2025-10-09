/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.elasticjob.v3_0;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeAttributesGetter;
import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

class ElasticJobSpanNameExtractor implements SpanNameExtractor<ElasticJobProcessRequest> {
  private final SpanNameExtractor<ElasticJobProcessRequest> codeSpanNameExtractor;

  ElasticJobSpanNameExtractor(CodeAttributesGetter<ElasticJobProcessRequest> getter) {
    this.codeSpanNameExtractor = CodeSpanNameExtractor.create(getter);
  }

  @Override
  public String extract(ElasticJobProcessRequest request) {
    if (request.isScriptJob()) {
      return "SCRIPT";
    }
    if (request.isHttpJob()) {
      return "HTTP";
    }
    return this.codeSpanNameExtractor.extract(request);
  }
}
