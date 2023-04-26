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
import org.apache.thrift.transport.TNonblockingSocket;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;

import java.lang.reflect.Field;

import static io.opentelemetry.javaagent.instrumentation.thrift.ThriftSingletons.clientInstrumenter;
import static io.opentelemetry.javaagent.instrumentation.thrift.ThriftSingletons.serverInstrumenter;

public class ServerInProtocolWrapper extends AbstractProtocolWrapper{
  public ThriftRequest request;
  public Context context;
  public Scope scope;
  public ServerInProtocolWrapper(final TProtocol protocol){
    super(protocol);
    request = new ThriftRequest(protocol);

  }
  public TTransport getRTransport() {
    try{
      if(this.trans_ instanceof TSocket){
        return this.trans_;
      }else if(this.trans_ instanceof TNonblockingSocket){
      return this.trans_;}
      else{
        return getInner(this.trans_);
      }
    }catch (Throwable e){
      System.out.println(e.toString());
      return null;
    }

  }

  public TTransport getInner(TTransport trans) throws IllegalAccessException {
    if(trans instanceof TSocket){
      return trans;
    }else if(trans instanceof TNonblockingSocket){
      return trans;
    }else
      for(Field f:trans.getClass().getDeclaredFields()){
        f.setAccessible(true);
        Object tmp = f.get(trans);
        if(tmp instanceof TTransport){
          if(tmp instanceof TSocket){
            return (TSocket)tmp;
          }else if(f.get(trans) instanceof TNonblockingSocket){
            return (TNonblockingSocket)tmp;
          }else{
            return getInner((TTransport)tmp);
          }
        }
      }
    return trans;
  }

  @Override
  public TMessage readMessageBegin() throws TException {
    TMessage msg = super.readMessageBegin();
    this.request.methodName = msg.name;
    return msg;
  }

  @Override
  public TField readFieldBegin() throws TException {
    final TField field = super.readFieldBegin();
    if(field.id == OIJ_THRIFT_FIELD_ID && field.type == TType.MAP){
      try {
        TMap tmap = super.readMapBegin();
        for (int i = 0; i < tmap.size; i++) {
          String a = readString();
          String b = readString();
          request.setAttachment(a, b);
        }
        Context context = Context.current();
        GlobalOpenTelemetry.get().getPropagators().getTextMapPropagator().extract(context, request,
            ThriftHeaderGetter.INSTANCE);
        if (!serverInstrumenter().shouldStart(context, request)) {
          return readFieldBegin();
        }
          context = serverInstrumenter().start(context, request);
          serverInstrumenter().end(context, request, 0, null);
          this.context = context;
          try{
            scope = context.makeCurrent();
          }
          catch (Throwable e){
            System.out.println(e.toString());
            scope.close();
            serverInstrumenter().end(context, request, 0, e);
          }
      }catch (Throwable e){
        System.out.println(e.toString());
      }finally{
        super.readMapEnd();
        super.readFieldEnd();
      }
      return readFieldBegin();
    }
    return field;
  }

}


