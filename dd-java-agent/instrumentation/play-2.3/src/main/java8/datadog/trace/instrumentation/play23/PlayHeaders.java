package datadog.trace.instrumentation.play23;

import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import play.api.mvc.Headers;
import scala.Option;
import scala.collection.JavaConversions;

public class PlayHeaders implements AgentPropagation.Getter<Headers> {

  public static final PlayHeaders GETTER = new PlayHeaders();

  @Override
  public Iterable<String> keys(final Headers headers) {
    return JavaConversions.asJavaIterable(headers.keys());
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
