package io.opentelemetry.javaagent.instrumentation.thrift;

import io.opentelemetry.context.propagation.TextMapSetter;

public enum ThriftHeaderSetter implements TextMapSetter<ThriftRequest>{
  INSTANCE;

  @Override
  public void set(ThriftRequest request, String key, String value) {
    request.setAttachment(key, value);
  }
}

