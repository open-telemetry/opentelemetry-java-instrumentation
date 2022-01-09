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

  public String someMethod(String name) {
    return "Hello " + name;
  }

  @Override
  public void exceptional() {
    throw new IllegalStateException("expected");
  }
}
