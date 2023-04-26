package io.opentelemetry.javaagent.instrumentation.thrift;

import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TProtocolFactory;
import org.apache.thrift.transport.TTransport;

public class ServerProtocolFactoryWrapper implements  TProtocolFactory{
  public TProtocolFactory innerProtocolFactoryWrapper;
  @Override
  public TProtocol getProtocol(TTransport tTransport) {
    TProtocol protocol = innerProtocolFactoryWrapper.getProtocol(tTransport);
    if(protocol instanceof ServerInProtocolWrapper){
      return protocol;
    }
    return new ServerInProtocolWrapper(innerProtocolFactoryWrapper.getProtocol(tTransport));
  }
  public ServerProtocolFactoryWrapper(TProtocolFactory inner){
    innerProtocolFactoryWrapper = inner;
  }
}
