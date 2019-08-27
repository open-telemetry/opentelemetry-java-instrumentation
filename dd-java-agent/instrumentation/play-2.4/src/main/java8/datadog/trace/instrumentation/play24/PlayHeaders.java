package datadog.trace.instrumentation.play24;

import io.opentracing.propagation.TextMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import play.api.mvc.Request;
import scala.Tuple2;

public class PlayHeaders implements TextMap {
  private final Request request;

  public PlayHeaders(final Request request) {
    this.request = request;
  }

  @Override
  public Iterator<Map.Entry<String, String>> iterator() {
    final scala.collection.Map scalaMap = request.headers().toSimpleMap();
    final Map<String, String> javaMap = new HashMap<>(scalaMap.size());
    final scala.collection.Iterator<Tuple2<String, String>> scalaIterator = scalaMap.iterator();
    while (scalaIterator.hasNext()) {
      final Tuple2<String, String> tuple = scalaIterator.next();
      javaMap.put(tuple._1(), tuple._2());
    }
    return javaMap.entrySet().iterator();
  }

  @Override
  public void put(final String s, final String s1) {
    throw new IllegalStateException("play headers can only be extracted");
  }
}
