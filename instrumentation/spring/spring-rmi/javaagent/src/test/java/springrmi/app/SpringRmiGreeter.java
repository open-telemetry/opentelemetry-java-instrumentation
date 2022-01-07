package springrmi.app;

import java.rmi.RemoteException;

public interface SpringRmiGreeter {
	public String hello(String msg) throws RemoteException;
  public void exceptional() throws RemoteException;
}
