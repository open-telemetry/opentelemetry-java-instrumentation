/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package rmi.app;

import java.rmi.RemoteException;

public interface ChildValue extends ParentValue {
  @Override
  String value() throws RemoteException;
}
