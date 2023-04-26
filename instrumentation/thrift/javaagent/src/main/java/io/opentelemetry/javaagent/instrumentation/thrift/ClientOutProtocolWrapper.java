package io.opentelemetry.javaagent.instrumentation.thrift;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import org.apache.thrift.TException;
import org.apache.thrift.protocol.TField;
import org.apache.thrift.protocol.TMap;
import org.apache.thrift.protocol.TMessage;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.protocol.TType;

import static io.opentelemetry.javaagent.instrumentation.thrift.ThriftSingletons.clientInstrumenter;
import static io.opentelemetry.javaagent.instrumentation.thrift.ThriftSingletons.serverInstrumenter;

public class ClientOutProtocolWrapper extends AbstractProtocolWrapper{
  public ThriftRequest request;
  private boolean injected = true;
  public Scope scope;
  public Context context;
  public ClientOutProtocolWrapper(final TProtocol protocol){
    super(protocol);
    request = new ThriftRequest(protocol);
  }

  @Override
  public final void writeMessageBegin(final TMessage message) throws TException {
    this.request.methodName = message.name;
    super.writeMessageBegin(message);
    injected = false;
//    System.out.println("WriteMessageBegin");
  }

  @Override
  public final void writeFieldStop() throws TException{
    if(!injected){
      Thread.dumpStack();
      Context context = Context.current();
      if(!clientInstrumenter().shouldStart(context,request)){
        return;
      }
      try{
        context = clientInstrumenter().start(context,request);
        this.context = context;
          GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator()
              .inject(context, request, ThriftHeaderSetter.INSTANCE);
          request.writeAttachment();
      }catch(Throwable e){
        clientInstrumenter().end(context,request,0,e);
      }finally {
        injected = true;
      }
    }
    super.writeFieldStop();
  }


}
