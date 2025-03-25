/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import io.opentelemetry.instrumentation.api.incubator.semconv.code.CodeSpanNameExtractor;
import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;
import io.opentelemetry.javaagent.bootstrap.NocodeInstrumentationRules;

public class NocodeSpanNameExtractor implements SpanNameExtractor<NocodeMethodInvocation> {
  private final SpanNameExtractor<ClassAndMethod> defaultNamer;

  public NocodeSpanNameExtractor() {
    this.defaultNamer = CodeSpanNameExtractor.create(ClassAndMethod.codeAttributesGetter());
  }

  @Override
  public String extract(NocodeMethodInvocation mi) {
    NocodeInstrumentationRules.Rule rule = mi.getRule();
    if (rule != null && rule.spanName != null) {
      Object name = mi.evaluate(rule.spanName);
      if (name != null) {
        return name.toString();
      }
    }
    return defaultNamer.extract(mi.getClassAndMethod());
  }
}
