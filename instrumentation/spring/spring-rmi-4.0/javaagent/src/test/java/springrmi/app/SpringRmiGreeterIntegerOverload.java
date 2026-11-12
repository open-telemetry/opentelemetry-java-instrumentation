/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springrmi.app;

import java.rmi.RemoteException;

public interface SpringRmiGreeterIntegerOverload {
  String overloaded(Integer value) throws RemoteException;
}
