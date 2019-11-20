package datadog.opentracing.scopemanager;

import io.opentracing.Span;
import java.io.Closeable;

public interface DDScope extends Closeable {

  Span span();

  @Override
  void close();
}
