package rmi.app;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

public class Server extends UnicastRemoteObject implements Greeter {
  static String RMI_ID = Server.class.getSimpleName();

  private static final long serialVersionUID = 1L;

  public Server() throws RemoteException {
    super();
  }

  @Override
  public String hello(final String name) {
    return "Hello " + name;
  }

  @Override
  public void exceptional() throws RuntimeException {
    throw new RuntimeException("expected");
  }
}
