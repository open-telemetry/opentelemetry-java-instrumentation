/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springrmi.app.ejb;

import java.rmi.Remote;
import java.rmi.RemoteException;
import springrmi.app.SpringRmiGreeter;

class SpringRmiGreeterRemote implements Remote {

  private final SpringRmiGreeter impl;

  SpringRmiGreeterRemote(SpringRmiGreeter impl) {
    this.impl = impl;
  }

  public String hello(String name) throws RemoteException {
    return impl.hello(name);
  }
  ;

  public void exceptional() throws RemoteException {
    impl.exceptional();
  }
}
