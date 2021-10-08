/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.field.VirtualField;

public class VirtualFieldTestClasses {
  public static class ValidAdvice {
    public static void advice() {
      Runnable.class.getName();
      VirtualField.find(Key1.class, Context.class);
      Key2.class.getName();
      Key1.class.getName();
      VirtualField.find(Key2.class, Context.class);
    }
  }

  public static class TwoVirtualFieldsInTheSameClassAdvice {
    public static void advice() {
      VirtualField.find(Key1.class, Context.class);
      VirtualField.find(Key1.class, State.class);
    }
  }

  public static class NotUsingClassRefAdvice {
    public static void advice(Class<?> key, Class<?> context) {
      Key2.class.getName();
      Key1.class.getName();
      VirtualField.find(key, context);
    }
  }

  public static class PassingVariableAdvice {
    public static void advice() {
      Class<?> context = Context.class;
      VirtualField.find(Key1.class, context);
    }
  }

  public static class UsingArrayAsOwnerAdvice {
    public static void advice() {
      VirtualField.find(Key1[].class, Context.class);
    }
  }

  public static class UsingPrimitiveAsOwnerAdvice {
    public static void advice() {
      VirtualField.find(int.class, Context.class);
    }
  }

  public static class Key1 {}

  public static class Key2 {}

  public static class State {}
}
