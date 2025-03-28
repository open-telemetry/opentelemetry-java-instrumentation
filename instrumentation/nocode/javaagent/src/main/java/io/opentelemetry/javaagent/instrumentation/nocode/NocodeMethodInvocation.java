/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.nocode;

import io.opentelemetry.instrumentation.api.incubator.semconv.util.ClassAndMethod;
import io.opentelemetry.javaagent.bootstrap.nocode.NocodeEvaluation;
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

  public Object getThiz() {
    return thiz;
  }

  /**
   * Please be careful with this, it's directly tied to @Advice.AllArguments.
   *
   * @return @Advice.AllArguments - please be careful
   */
  public Object[] getParameters() {
    return parameters;
  }

  public ClassAndMethod getClassAndMethod() {
    return classAndMethod;
  }

  public Map<String, String> getRuleAttributes() {
    return rule == null ? Collections.emptyMap() : rule.getAttributes();
  }

  public Object evaluate(String expression) {
    return NocodeEvaluation.evaluate(expression, thiz, parameters);
  }

  public Object evaluateAtEnd(String expression, Object returnValue, Throwable error) {
    return NocodeEvaluation.evaluateAtEnd(expression, thiz, parameters, returnValue, error);
  }
}
