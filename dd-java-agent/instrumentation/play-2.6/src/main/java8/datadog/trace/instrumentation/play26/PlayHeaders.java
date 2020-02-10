package datadog.trace.instrumentation.play26;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import java.util.ArrayList;
import java.util.List;
import play.api.mvc.Headers;
import scala.Option;

public class PlayHeaders implements AgentPropagation.Getter<Headers> {

  public static final PlayHeaders GETTER = new PlayHeaders();

  @Override
  public Iterable<String> keys(final Headers headers) {
    final List<String> javaList = new ArrayList<>();
    final scala.collection.Iterator<String> scalaIterator = headers.keys().iterator();
    while (scalaIterator.hasNext()) {
      javaList.add(scalaIterator.next());
    }
    return javaList;
  }

  @Override
  public String get(final Headers headers, final String key) {
    final Option<String> option = headers.get(key);
    if (option.isDefined()) {
      return option.get();
    } else {
      return null;
    }
  }
}
