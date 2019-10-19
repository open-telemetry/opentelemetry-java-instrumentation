package datadog.trace.instrumentation.play24;

import datadog.trace.instrumentation.api.AgentPropagation;
import play.api.mvc.Request;
import scala.Option;
import scala.collection.JavaConversions;

public class PlayHeaders implements AgentPropagation.Getter<Request> {

  public static final PlayHeaders GETTER = new PlayHeaders();

  @Override
  public Iterable<String> keys(final Request carrier) {
    return JavaConversions.asJavaIterable(carrier.headers().keys());
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
