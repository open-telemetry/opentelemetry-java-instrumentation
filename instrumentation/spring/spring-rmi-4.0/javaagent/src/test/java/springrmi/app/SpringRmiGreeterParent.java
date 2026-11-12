/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springrmi.app;

import java.rmi.RemoteException;

public interface SpringRmiGreeterParent {
  String inherited(String name) throws RemoteException;

  String overloaded(String name) throws RemoteException;
}
