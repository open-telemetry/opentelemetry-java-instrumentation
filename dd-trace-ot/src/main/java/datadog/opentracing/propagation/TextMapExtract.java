package datadog.opentracing.propagation;

import java.util.Iterator;
import java.util.Map;

// temporary replacement for io.opentracing.propagation.TextMapExtract
public interface TextMapExtract extends Iterable<Map.Entry<String, String>> {
  @Override
  Iterator<Map.Entry<String, String>> iterator();
}
