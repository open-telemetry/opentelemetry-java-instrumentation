/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.context.Context;
import io.opentelemetry.javaagent.instrumentation.api.InstrumentationContext;

public class InstrumentationContextTestClasses {
  public static class ValidAdvice {
    public static void advice() {
      Runnable.class.getName();
      InstrumentationContext.get(Key1.class, Context.class);
      Key2.class.getName();
      Key1.class.getName();
      InstrumentationContext.get(Key2.class, Context.class);
    }
  }

  public static class TwoContextStoresAdvice {
    public static void advice() {
      InstrumentationContext.get(Key1.class, Context.class);
      InstrumentationContext.get(Key1.class, State.class);
    }
  }

  public static class NotUsingClassRefAdvice {
    public static void advice(Class<?> key, Class<?> context) {
      Key2.class.getName();
      Key1.class.getName();
      InstrumentationContext.get(key, context);
    }
  }

  public static class PassingVariableAdvice {
    public static void advice() {
      Class<?> context = Context.class;
      InstrumentationContext.get(Key1.class, context);
    }
  }

  public static class Key1 {}

  public static class Key2 {}

  public static class State {}
}
