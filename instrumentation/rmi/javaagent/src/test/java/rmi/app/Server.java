/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package rmi.app;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class Server extends UnicastRemoteObject implements Greeter, NumericGreeter, ChildValue {
  public static final String RMI_ID = Server.class.getSimpleName();

  private static final long serialVersionUID = 1L;

  public Server() throws RemoteException {
    super();
  }

  @Override
  public String hello(String name) {
    return someMethod(name);
  }

  @Override
  public String hello(int number) {
    return someMethod(Integer.toString(number));
  }

  private static String someMethod(String name) {
    return "Hello " + name;
  }

  @Override
  public void exceptional() {
    throw new IllegalStateException("expected");
  }

  @Override
  public String inherited(String name) {
    return someMethod(name);
  }

  @Override
  public Integer number(int value) {
    return value;
  }

  @Override
  public String covariant(String name) {
    return someMethod(name);
  }

  @Override
  public String shared(String name) {
    return someMethod(name);
  }

  @Override
  public String value() {
    return "value";
  }

  public static class Child extends Server {
    private static final long serialVersionUID = 1L;

    public Child() throws RemoteException {}
  }
}
