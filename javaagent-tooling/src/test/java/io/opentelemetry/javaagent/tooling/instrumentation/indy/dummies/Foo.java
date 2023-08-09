package io.opentelemetry.javaagent.tooling.instrumentation.indy.dummies;

public class Foo {
  private Foo() {}

  public static String foo(Bar bar) {
    return "foo";
  }
}
