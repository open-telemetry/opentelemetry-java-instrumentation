package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v5_0;

import io.opentelemetry.context.Context;
import java.io.IOException;
import java.io.OutputStream;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.HttpEntityWrapper;

public class WrappedHttpEntity extends HttpEntityWrapper {
  private final Context parentContext;

  public WrappedHttpEntity(Context parentContext, HttpEntity wrappedEntity) {
    super(wrappedEntity);
    this.parentContext = parentContext;
  }

  @Override
  public void writeTo(OutputStream outStream) throws IOException {
    super.writeTo(new CountingOutputStream(parentContext, outStream));
  }
}
