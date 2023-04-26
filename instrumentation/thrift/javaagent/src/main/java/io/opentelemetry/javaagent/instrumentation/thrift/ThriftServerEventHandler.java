package io.opentelemetry.javaagent.instrumentation.thrift;

import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.ServerContext;
import org.apache.thrift.server.TServerEventHandler;
import org.apache.thrift.transport.TTransport;

public class ThriftServerEventHandler implements TServerEventHandler {
  public TServerEventHandler innerEventHandler;
  public ThriftServerEventHandler(TServerEventHandler inner){
    if(inner instanceof ThriftServerEventHandler){
      innerEventHandler = ((ThriftServerEventHandler) inner).innerEventHandler;
    }
    else{
    innerEventHandler = inner;}
  }

  public void preServe()
  {
    if(innerEventHandler!=null){
    innerEventHandler.preServe();}
  }

  public ServerContext createContext(TProtocol tProtocol, TProtocol tProtocol1) {
    if(innerEventHandler!=null){
      return innerEventHandler.createContext(tProtocol, tProtocol1);
    }
    return null;
  }

  /**
   * 删除Context的时候，触发
   * 在server启动后，只会执行一次
   */
  public void deleteContext(ServerContext serverContext, TProtocol input, TProtocol output)
  {
    if(innerEventHandler!=null)
    {
      innerEventHandler.deleteContext(serverContext, input, output);
    }
  }

  @Override
  public void processContext(ServerContext serverContext, TTransport tTransport,
      TTransport tTransport1) {
    if(innerEventHandler!=null)
    {
      innerEventHandler.processContext(serverContext,tTransport,tTransport1);}
    }

}


