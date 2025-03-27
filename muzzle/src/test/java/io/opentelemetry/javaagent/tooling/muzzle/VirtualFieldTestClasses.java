/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.muzzle;

import io.opentelemetry.context.Context;
import io.opentelemetry.instrumentation.api.util.VirtualField;

@SuppressWarnings({"ReturnValueIgnored", "unused"})
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

  public static class VirtualFieldInStaticInitializerAdvice {

    static final VirtualField<Key1, Context> FIELD_1 = VirtualField.find(Key1.class, Context.class);

    public static class Helper {
      private Helper() {}

      static final VirtualField<Key2, Context> FIELD_2 =
          VirtualField.find(Key2.class, Context.class);

      public static void foo() {}
    }

    public static void advice() {
      Helper.foo();
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

  public static class UsingArrayAsFieldAdvice {
    public static void advice() {
      VirtualField.find(Key1.class, Context[].class);
    }
  }

  public static class UsingPrimitiveAsFieldAdvice {
    public static void advice() {
      VirtualField.find(Key1.class, int.class);
    }
  }

  public static class Key1 {}

  public static class Key2 {}

  public static class State {}

  private VirtualFieldTestClasses() {}
}
