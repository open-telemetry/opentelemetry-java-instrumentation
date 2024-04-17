/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springrmi.app.ejb;

import java.rmi.Remote;
import java.rmi.RemoteException;
import springrmi.app.SpringRmiGreeter;

// Real EJBs auto-generate a stub class similar to this. As we don't have a way to use the actual
// auto-generated class, this wrapper is used to emulate it.
class SpringRmiGreeterRemote implements Remote {

  private final SpringRmiGreeter impl;

  SpringRmiGreeterRemote(SpringRmiGreeter impl) {
    this.impl = impl;
  }

  public String hello(String name) throws RemoteException {
    return impl.hello(name);
  }

  public void exceptional() throws RemoteException {
    impl.exceptional();
  }
}
