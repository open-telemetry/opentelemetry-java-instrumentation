/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.bootstrap;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class NocodeInstrumentationRules {

  // FIXME builder
  public static final class Rule {
    public final String className;
    public final String methodName;
    public final String spanName; // may be null - use default of "class.method"
    public final String spanKind; // matches the SpanKind enum, null means default to INTERNAL
    public final String spanStatus; // may be null, should return string from StatusCodes

    public final Map<String, String> attributes; // key name to jexl expression

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

  // FIXME setting the global and lookup could go away if the instrumentation could be parameterized with the Rule
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
