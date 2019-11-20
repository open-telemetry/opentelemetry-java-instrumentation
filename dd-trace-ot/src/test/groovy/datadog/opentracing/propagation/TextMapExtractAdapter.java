package datadog.opentracing.propagation;

import java.util.Iterator;
import java.util.Map;

// temporary replacement for io.opentracing.propagation.TextMapExtractAdapter
public class TextMapExtractAdapter implements TextMapExtract {
  protected final Map<String, String> map;

  public TextMapExtractAdapter(final Map<String, String> map) {
    this.map = map;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    return map.entrySet().iterator();
  }
}
