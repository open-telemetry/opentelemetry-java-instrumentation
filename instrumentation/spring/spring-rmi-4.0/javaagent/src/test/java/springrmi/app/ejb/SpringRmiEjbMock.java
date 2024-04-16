/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package springrmi.app.ejb;

import static io.opentelemetry.javaagent.bootstrap.rmi.ThreadLocalContext.THREAD_LOCAL_CONTEXT;

import java.lang.reflect.Method;
import java.rmi.RemoteException;
import springrmi.app.SpringRmiGreeter;

// Wrapper around SpringRmiGreeter to mock an EJB
// Required because you can't dynamically register EJBs during runtime
public class SpringRmiEjbMock implements SpringRmiGreeter {

  private final SpringRmiGreeterRemote proxy;

  public SpringRmiEjbMock(SpringRmiGreeter greeter) {
    this.proxy = new SpringRmiGreeterRemote(greeter);
  }

  /*
  The RMI instrumentation relies on the RMI Dispatcher to add the Context into the
  THREAD_LOCAL_CONTEXT wrapper. Because this EJB Mock doesn't go through the RMI Dispatcher, we
  need to add it to the wrapper manually. The problem is that the bootstrap import we obtain the
  wrapper from uses the shaded OTel classes. Attempting to use the .set() method using the
  non-shaded Context class will throw an exception, so we have to use reflection to use the
  shaded classes.
  */
  private static void setThreadLocalContext() {
    try {
      Class<?> clazz =
          ClassLoader.getSystemClassLoader()
              .loadClass("io.opentelemetry.javaagent.shaded.io.opentelemetry.context.Context");
      Method setter = THREAD_LOCAL_CONTEXT.getClass().getMethod("set", clazz);
      setter.invoke(THREAD_LOCAL_CONTEXT, clazz.getMethod("current").invoke(null));
    } catch (Exception e) {
      // ignore
    }
  }

  @Override
  public String hello(String name) throws RemoteException {
    setThreadLocalContext();
    return proxy.hello(name);
  }

  @Override
  public void exceptional() throws RemoteException {
    setThreadLocalContext();
    proxy.exceptional();
  }
}
