/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package rmi.app;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class Server extends UnicastRemoteObject implements Greeter {
  public static String RMI_ID = Server.class.getSimpleName();

  private static final long serialVersionUID = 1L;

  public Server() throws RemoteException {
    super();
  }

  @Override
  public String hello(String name) {
    return someMethod(name);
  }

  public String someMethod(String name) {
    return "Hello " + name;
  }

  @Override
  public void exceptional() throws RuntimeException {
    throw new RuntimeException("expected");
  }
}
