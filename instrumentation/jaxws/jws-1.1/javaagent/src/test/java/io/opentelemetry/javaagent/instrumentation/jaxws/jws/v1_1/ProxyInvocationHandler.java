package io.opentelemetry.javaagent.instrumentation.jaxws.jws.v1_1;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

public class ProxyInvocationHandler implements InvocationHandler {

  WebServiceDefinitionInterface target;

  public ProxyInvocationHandler(WebServiceFromInterface webServiceFromInterface) {
    target = webServiceFromInterface;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    return method.invoke(target, args);
  }

  public static void main(String[] args) {
    WebServiceDefinitionInterface proxy =
        (WebServiceDefinitionInterface)
            Proxy.newProxyInstance(
                WebServiceFromInterface.class.getClassLoader(),
                new Class<?>[] {WebServiceDefinitionInterface.class},
                new ProxyInvocationHandler(new WebServiceFromInterface()));
    proxy.partOfPublicInterface();
  }
}
