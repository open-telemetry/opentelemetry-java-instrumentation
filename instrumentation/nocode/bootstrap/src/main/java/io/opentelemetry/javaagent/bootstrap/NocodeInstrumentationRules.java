/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NocodeInstrumentationRules {

  public final static class Builder {
    private String className;
    private String methodName;
    private String spanName;
    private String spanKind;
    private String spanStatus;
    private final Map<String, String> attributes = new HashMap<>();

    @CanIgnoreReturnValue
    public Builder className(String className) {
      this.className = className;
      return this;
    }
    @CanIgnoreReturnValue
    public Builder methodName(String methodName) {
      this.methodName = methodName;
      return this;
    }
    @CanIgnoreReturnValue
    public Builder spanName(String spanName) {
      this.spanName = spanName;
      return this;
    }
    @CanIgnoreReturnValue
    public Builder spanKind(String spanKind) {
      this.spanKind = spanKind;
      return this;
    }
    @CanIgnoreReturnValue
    public Builder spanStatus(String spanStatus) {
      this.spanStatus = spanStatus;
      return this;
    }
    @CanIgnoreReturnValue
    public Builder attribute(String key, String valueExpression) {
      attributes.put(key, valueExpression);
      return this;
    }

    public Rule build() {
      return new Rule(className, methodName, spanName, spanKind, spanStatus, attributes);
    }
  }

  public static final class Rule {
    private final String className;
    private final String methodName;
    private final String spanName; // may be null - use default of "class.method"
    private final String spanKind; // matches the SpanKind enum, null means default to INTERNAL
    private final String spanStatus; // may be null, should return string from StatusCodes
    private final Map<String, String> attributes; // key name to jexl expression

    public Rule(
        String className,
        String methodName,
        String spanName,
        String spanKind,
        String spanStatus,
        Map<String, String> attributes) {
      this.className = className;
      this.methodName = methodName;
      this.spanName = spanName;
      this.spanKind = spanKind;
      this.spanStatus = spanStatus;
      this.attributes = Collections.unmodifiableMap(new HashMap<>(attributes));
    }

    public Map<String, String> getAttributes() {
      return attributes;
    }
    public String getClassName() {
      return className;
    }
    public String getMethodName() {
      return methodName;
    }
    public String getSpanName() {
      return spanName;
    }
    public String getSpanKind() {
      return spanKind;
    }
    public String getSpanStatus() {
      return spanStatus;
    }

    @Override
    public String toString() {
      return "Nocode rule: "
          + className
          + "."
          + methodName
          + ":spanName="
          + spanName
          + ":spanKind="
          + spanKind
          + ":spanStatus="
          + spanStatus
          + ",attrs="
          + attributes;
    }
  }

  private NocodeInstrumentationRules() {}

  // FUTURE setting the global and lookup could go away if the instrumentation could be parameterized
  // with the Rule

  // Using className.methodName as the key
  private static final HashMap<String, Rule> name2Rule = new HashMap<>();

  // Called by the NocodeInitializer
  public static void setGlobalRules(List<Rule> rules) {
    for (Rule r : rules) {
      name2Rule.put(r.className + "." + r.methodName, r);
    }
  }

  public static Iterable<Rule> getGlobalRules() {
    return name2Rule.values();
  }

  public static Rule find(String className, String methodName) {
    return name2Rule.get(className + "." + methodName);
  }
}
