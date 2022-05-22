/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springrmi.app;

import java.rmi.RemoteException;

public interface SpringRmiGreeter {
  String hello(String name) throws RemoteException;

  void exceptional() throws RemoteException;
}
