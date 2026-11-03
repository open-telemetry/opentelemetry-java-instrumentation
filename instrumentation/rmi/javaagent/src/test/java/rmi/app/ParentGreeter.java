/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package rmi.app;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ParentGreeter extends Remote {
  String inherited(String name) throws RemoteException;

  Number number(int value) throws RemoteException;

  Object covariant(String name) throws RemoteException;
}
