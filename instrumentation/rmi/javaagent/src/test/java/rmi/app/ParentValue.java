/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package rmi.app;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface ParentValue extends Remote {
  Object value() throws RemoteException;
}
