/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springrmi.app;

public class SpringRmiGreeterImpl implements SpringRmiGreeter {

  @Override
  public String hello(String name) {
    return someMethod(name);
  }

  @Override
  public String inherited(String name) {
    return "Inherited " + name;
  }

  @Override
  public String overloaded(String name) {
    return "String " + name;
  }

  @Override
  public String overloaded(Integer value) {
    return "Integer " + value;
  }

  private static String someMethod(String name) {
    return "Hello " + name;
  }

  @Override
  public void exceptional() {
    throw new IllegalStateException("expected");
  }
}
