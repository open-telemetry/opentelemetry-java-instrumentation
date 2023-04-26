package io.opentelemetry.javaagent.instrumentation.thrift;

import io.opentelemetry.context.Context;
import org.apache.thrift.async.AsyncMethodCallback;

import static io.opentelemetry.javaagent.instrumentation.thrift.ThriftSingletons.clientInstrumenter;

public class AsyncMethodCallbackWrapper<T> implements AsyncMethodCallback<T> {
  private AsyncMethodCallback innerCallback;
  public Context context;
  public ThriftRequest request;
  public AsyncMethodCallbackWrapper(AsyncMethodCallback inner){
    innerCallback = inner;
  }

  public void SetContext(Context text){
    context = text;
  }

  public Context getContext(){
    return this.context;
  }

  @Override
  public void onComplete(T t) {
    clientInstrumenter().end(context,request,0,null);
    innerCallback.onComplete(t);
  }

  @Override
  public void onError(Exception e) {
    innerCallback.onError(e);
    clientInstrumenter().end(context,request,0,e);
  }
}
