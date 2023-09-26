package indy;

import library.MyProxySuperclass;
import java.util.concurrent.Callable;

public class ProxyMe extends MyProxySuperclass implements Callable<String> {

  @Override
  public String call() {
    return "Hi from ProxyMe";
  }


  public static String staticHello() {
    return "Hi from static";
  }
}
