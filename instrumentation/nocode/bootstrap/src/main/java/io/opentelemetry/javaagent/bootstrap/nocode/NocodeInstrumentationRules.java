/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap.nocode;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.api.trace.SpanKind;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public final class NocodeInstrumentationRules {

  public static final class Builder {
    private String className;
    private String methodName;
    private NocodeExpression spanName;
    private SpanKind spanKind;
    private NocodeExpression spanStatus;
    private final Map<String, NocodeExpression> attributes = new HashMap<>();

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
    public Builder spanName(NocodeExpression spanName) {
      this.spanName = spanName;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder spanKind(SpanKind spanKind) {
      this.spanKind = spanKind;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder spanStatus(NocodeExpression spanStatus) {
      this.spanStatus = spanStatus;
      return this;
    }

    @CanIgnoreReturnValue
    public Builder attribute(String key, NocodeExpression valueExpression) {
      attributes.put(key, valueExpression);
      return this;
    }

    public Rule build() {
      return new Rule(className, methodName, spanName, spanKind, spanStatus, attributes);
    }
  }

  public static final class Rule {
    private static final AtomicInteger counter = new AtomicInteger();

    private final int id = counter.incrementAndGet();
    private final String className;
    private final String methodName;
    private final NocodeExpression spanName; // may be null - use default of "class.method"
    private final SpanKind spanKind; // may be null
    private final NocodeExpression spanStatus; // may be null, should return string from StatusCodes
    private final Map<String, NocodeExpression> attributes; // key name to jexl expression

    public Rule(
        String className,
        String methodName,
        NocodeExpression spanName,
        SpanKind spanKind,
        NocodeExpression spanStatus,
        Map<String, NocodeExpression> attributes) {
      this.className = className;
      this.methodName = methodName;
      this.spanName = spanName;
      this.spanKind = spanKind;
      this.spanStatus = spanStatus;
      this.attributes = Collections.unmodifiableMap(new HashMap<>(attributes));
    }

    public int getId() {
      return id;
    }

    public Map<String, NocodeExpression> getAttributes() {
      return attributes;
    }

    public String getClassName() {
      return className;
    }

    public String getMethodName() {
      return methodName;
    }

    public NocodeExpression getSpanName() {
      return spanName;
    }

    public SpanKind getSpanKind() {
      return spanKind;
    }

    public NocodeExpression getSpanStatus() {
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

  // FUTURE setting the global and lookup could go away if the instrumentation could be
  // parameterized with the Rule

  private static final HashMap<Integer, Rule> ruleMap = new HashMap<>();

  // Called by the NocodeInitializer
  public static void setGlobalRules(List<Rule> rules) {
    for (Rule r : rules) {
      ruleMap.put(r.id, r);
    }
  }

  public static Iterable<Rule> getGlobalRules() {
    return ruleMap.values();
  }

  public static Rule find(int id) {
    return ruleMap.get(id);
  }
}
