package io.opentelemetry.auto.instrumentation.akkahttp;

import akka.http.javadsl.model.HttpHeader;
import akka.http.scaladsl.model.HttpRequest;
import io.opentelemetry.context.propagation.HttpTextFormat;
import java.util.Optional;

public class AkkaHttpServerHeaders implements HttpTextFormat.Getter<HttpRequest> {

  public static final AkkaHttpServerHeaders GETTER = new AkkaHttpServerHeaders();

  @Override
  public String get(final HttpRequest carrier, final String key) {
    final Optional<HttpHeader> header = carrier.getHeader(key);
    if (header.isPresent()) {
      return header.get().value();
    } else {
      return null;
    }
  }
}
