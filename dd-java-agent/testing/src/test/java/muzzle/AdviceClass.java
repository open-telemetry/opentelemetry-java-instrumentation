package muzzle;

import net.bytebuddy.asm.Advice;

public class AdviceClass {

  @Advice.OnMethodEnter
  public static void advice() {
    A a = new A();
    SomeInterface inter = new SomeImplementation();
    inter.someMethod();
  }

  public static class A {}

  public interface SomeInterface {
    void someMethod();
  }

  public static class SomeImplementation implements SomeInterface {
    @Override
    public void someMethod() {}
  }
}
