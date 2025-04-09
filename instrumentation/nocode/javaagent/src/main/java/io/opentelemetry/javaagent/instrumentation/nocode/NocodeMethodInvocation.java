/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.javaagent.bootstrap.nocode.NocodeExpression;
import io.opentelemetry.javaagent.bootstrap.nocode.NocodeInstrumentationRules;
import java.util.Collections;
import java.util.Map;

public final class NocodeMethodInvocation {
  private final NocodeInstrumentationRules.Rule rule;
  private final ClassAndMethod classAndMethod;
  private final Object thiz;
  private final Object[] parameters;

  public NocodeMethodInvocation(
      NocodeInstrumentationRules.Rule rule, ClassAndMethod cm, Object thiz, Object[] parameters) {
    this.rule = rule;
    this.classAndMethod = cm;
    this.thiz = thiz;
    this.parameters = parameters;
  }

  public NocodeInstrumentationRules.Rule getRule() {
    return rule;
  }

  public ClassAndMethod getClassAndMethod() {
    return classAndMethod;
  }

  public Map<String, NocodeExpression> getRuleAttributes() {
    return rule == null ? Collections.emptyMap() : rule.getAttributes();
  }

  public Object evaluate(NocodeExpression expression) {
    return expression.evaluate(thiz, parameters);
  }

  public Object evaluateAtEnd(NocodeExpression expression, Object returnValue, Throwable error) {
    return expression.evaluateAtEnd(thiz, parameters, returnValue, error);
  }
}
