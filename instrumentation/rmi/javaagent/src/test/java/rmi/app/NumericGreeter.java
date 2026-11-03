/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package rmi.app;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface NumericGreeter extends Remote {
  String hello(int number) throws RemoteException;

  Integer number(int value) throws RemoteException;

  String shared(String name) throws RemoteException;
}
