package io.opentelemetry.auto.instrumentation.play26;

import io.opentelemetry.context.propagation.HttpTextFormat;
import play.api.mvc.Headers;
import scala.Option;

public class PlayHeaders implements HttpTextFormat.Getter<Headers> {

  public static final PlayHeaders GETTER = new PlayHeaders();

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
