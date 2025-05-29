/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package muzzle;

import external.instrumentation.ExternalHelper;
import io.opentelemetry.instrumentation.OtherTestHelperClasses;
import io.opentelemetry.instrumentation.TestHelperClasses.Helper;
import net.bytebuddy.asm.Advice;

@SuppressWarnings("unused")
public class TestClasses {

  public static class MethodBodyAdvice {
    @Advice.OnMethodEnter
    @SuppressWarnings("ReturnValueIgnored")
    public static void methodBodyAdvice() {
      Nested.A a = new Nested.A();
      Nested.SomeInterface inter = new Nested.SomeImplementation();
      inter.someMethod();
      a.publicB.method("foo");
      a.publicB.methodWithPrimitives(false);
      a.publicB.methodWithArrays(new String[0]);
      Nested.B.staticMethod();
      Nested.A.staticB.method("bar");
      new int[0].clone();
    }
  }

  @SuppressWarnings("ClassNamedLikeTypeParameter")
  public static class Nested {
    public static class A {
      public B publicB = new B();
      protected Object protectedField = null;
      private final Object privateField = null;
      public static B staticB = new B();
    }

    public static class B {
      public String method(String s) {
        return s;
      }

      public void methodWithPrimitives(boolean b) {}

      public Object[] methodWithArrays(String[] s) {
        return s;
      }

      @SuppressWarnings("MethodCanBeStatic")
      private void privateStuff() {}

      protected void protectedMethod() {}

      public static void staticMethod() {}
    }

    public static class B2 extends B {
      public void stuff() {
        B b = new B();
        b.protectedMethod();
      }
    }

    public static class A2 extends A {}

    public static class Primitives {
      int number = 1;
      boolean flag = false;
    }

    public interface SomeInterface {
      void someMethod();
    }

    public static class SomeImplementation implements SomeInterface {
      @Override
      public void someMethod() {}
    }

    public static class SomeClassWithFields {
      public int instanceField = 0;
      public static int staticField = 0;
      public final int finalField = 0;
    }

    public interface AnotherInterface extends SomeInterface {}

    private Nested() {}
  }

  public abstract static class BaseClassWithConstructor {
    protected BaseClassWithConstructor(long l) {}
  }

  public static class LdcAdvice {
    @SuppressWarnings("ReturnValueIgnored")
    public static void ldcMethod() {
      Nested.A.class.getName();
    }
  }

  public static class InstanceofAdvice {
    public static boolean instanceofMethod(Object a) {
      return a instanceof Nested.A;
    }
  }

  public static class InvokeDynamicAdvice {
    @SuppressWarnings("UnnecessaryMethodReference")
    public static Nested.SomeInterface invokeDynamicMethod(Nested.SomeImplementation a) {
      Runnable staticMethod = Nested.B::staticMethod;
      Runnable constructorMethod = Nested.A::new;
      return a::someMethod;
    }
  }

  public static class HelperAdvice {
    public static void adviceMethod() {
      Helper h = new Helper();
    }
  }

  public static class HelperOtherAdvice {
    public static void adviceMethod() {
      new OtherTestHelperClasses.Bar().doSomething();
    }
  }

  public static class ExternalInstrumentationAdvice {
    public static void adviceMethod() {
      new ExternalHelper().instrument();
    }
  }

  private TestClasses() {}
}
