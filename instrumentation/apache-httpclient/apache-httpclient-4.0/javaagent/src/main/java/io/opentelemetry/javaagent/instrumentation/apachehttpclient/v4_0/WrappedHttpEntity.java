package io.opentelemetry.javaagent.instrumentation.apachehttpclient.v4_0;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntity;

public class WrappedHttpEntity implements HttpEntity {
  private final ApacheHttpClientRequest request;
  private final HttpEntity delegate;

  public WrappedHttpEntity(ApacheHttpClientRequest request, HttpEntity delegate) {
    this.request = request;
    this.delegate = delegate;
  }

  @Override
  public boolean isRepeatable() {
    return delegate.isRepeatable();
  }

  @Override
  public boolean isChunked() {
    return delegate.isChunked();
  }

  @Override
  public long getContentLength() {
    return delegate.getContentLength();
  }

  @Override
  public Header getContentType() {
    return delegate.getContentType();
  }

  @Override
  public Header getContentEncoding() {
    return delegate.getContentEncoding();
  }

  @Override
  public InputStream getContent() throws IOException {
    return delegate.getContent();
  }

  @Override
  public void writeTo(OutputStream outStream) throws IOException {
    delegate.writeTo(new CountingOutputStream(request, outStream));
  }

  @Override
  public boolean isStreaming() {
    return delegate.isStreaming();
  }

  @Override
  public void consumeContent() throws IOException {
    delegate.consumeContent();
  }
}
