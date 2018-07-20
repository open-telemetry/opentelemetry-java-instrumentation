package muzzle;

import net.bytebuddy.asm.Advice;

public class TestClasses {

  public static class MethodBodyAdvice {
    @Advice.OnMethodEnter
    public static void methodBodyAdvice() {
      A a = new A();
      SomeInterface inter = new SomeImplementation();
      inter.someMethod();
      a.b.aMethod("foo");
      a.b.aMethodWithPrimitives(false);
      a.b.aMethodWithArrays(new String[0]);
    }

    public static class A {
      public B b = new B();
    }

    public static class B {
      public String aMethod(String s) { return s; }
      public void aMethodWithPrimitives(boolean b) {}
      public Object[] aMethodWithArrays(String[] s) { return s; }
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

    public interface AnotherInterface extends SomeInterface { }
  }

}
