package datadog.trace.instrumentation.play26;

import datadog.trace.instrumentation.api.AgentPropagation;
import java.util.ArrayList;
import java.util.List;
import play.api.mvc.Request;
import scala.Option;

public class PlayHeaders implements AgentPropagation.Getter<Request> {

  public static final PlayHeaders GETTER = new PlayHeaders();

  @Override
  public Iterable<String> keys(final Request carrier) {
    final List<String> javaList = new ArrayList<>();
    final scala.collection.Iterator<String> scalaIterator = carrier.headers().keys().iterator();
    while (scalaIterator.hasNext()) {
      javaList.add(scalaIterator.next());
    }
    return javaList;
  }

  @Override
  public String get(final Request carrier, final String key) {
    final Option<String> option = carrier.headers().get(key);
    if (option.isDefined()) {
      return option.get();
    } else {
      return null;
    }
  }
}
