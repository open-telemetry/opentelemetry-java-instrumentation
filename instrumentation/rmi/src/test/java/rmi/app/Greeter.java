package rmi.app;

import java.rmi.Remote;
import java.rmi.RemoteException;

public interface Greeter extends Remote {
  String hello(String name) throws RemoteException;

  void exceptional() throws RemoteException, RuntimeException;
}
