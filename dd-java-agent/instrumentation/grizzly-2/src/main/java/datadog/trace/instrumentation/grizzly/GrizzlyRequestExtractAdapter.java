package datadog.trace.instrumentation.grizzly;

import io.opentracing.propagation.TextMap;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.glassfish.grizzly.http.server.Request;

public class GrizzlyRequestExtractAdapter implements TextMap {

  private final Map<String, List<String>> headers;

  public GrizzlyRequestExtractAdapter(final Request request) {
    headers = headersToMultiMap(request);
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    return new MultivaluedMapFlatIterator<>(headers.entrySet());
  }

  @Override
  public void put(final String key, final String value) {
    throw new UnsupportedOperationException("This class should be used only with Tracer.inject()!");
  }

  protected Map<String, List<String>> headersToMultiMap(final Request request) {
    final Map<String, List<String>> headersResult = new HashMap<>();

    for (final String headerName : request.getHeaderNames()) {
      final List<String> valuesList = new ArrayList<>(1);

      for (final String values : request.getHeaders(headerName)) {
        valuesList.add(values);
      }
      headersResult.put(headerName, valuesList);
    }

    return headersResult;
  }

  public static final class MultivaluedMapFlatIterator<K, V> implements Iterator<Map.Entry<K, V>> {

    private final Iterator<Map.Entry<K, List<V>>> mapIterator;
    private Map.Entry<K, List<V>> mapEntry;
    private Iterator<V> listIterator;

    public MultivaluedMapFlatIterator(final Set<Map.Entry<K, List<V>>> multiValuesEntrySet) {
      mapIterator = multiValuesEntrySet.iterator();
    }

    @Override
    public boolean hasNext() {
      if (listIterator != null && listIterator.hasNext()) {
        return true;
      }

      return mapIterator.hasNext();
    }

    @Override
    public Map.Entry<K, V> next() {
      if (mapEntry == null || (!listIterator.hasNext() && mapIterator.hasNext())) {
        mapEntry = mapIterator.next();
        listIterator = mapEntry.getValue().iterator();
      }

      if (listIterator.hasNext()) {
        return new AbstractMap.SimpleImmutableEntry<>(mapEntry.getKey(), listIterator.next());
      } else {
        return new AbstractMap.SimpleImmutableEntry<>(mapEntry.getKey(), null);
      }
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }
}
