package datadog.trace.instrumentation.akkahttp;

import akka.http.javadsl.model.HttpHeader;
import akka.http.scaladsl.model.HttpRequest;
import datadog.trace.bootstrap.instrumentation.api.AgentPropagation;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class AkkaHttpServerHeaders implements AgentPropagation.Getter<HttpRequest> {

  public static final AkkaHttpServerHeaders GETTER = new AkkaHttpServerHeaders();

  @Override
  public List<String> keys(final HttpRequest carrier) {
    final List<String> keys = new ArrayList<>();
    for (final HttpHeader header : carrier.getHeaders()) {
      keys.add(header.name());
    }
    return keys;
  }

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
