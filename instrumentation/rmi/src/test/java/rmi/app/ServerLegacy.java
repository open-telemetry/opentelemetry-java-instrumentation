/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package rmi.app;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class ServerLegacy extends UnicastRemoteObject implements Greeter {
  static String RMI_ID = ServerLegacy.class.getSimpleName();

  private static final long serialVersionUID = 1L;

  public ServerLegacy() throws RemoteException {
    super();
  }

  @Override
  public String hello(String name) {
    return "Hello " + name;
  }

  @Override
  public void exceptional() throws RuntimeException {
    throw new RuntimeException("expected");
  }
}
