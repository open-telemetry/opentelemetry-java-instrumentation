package io.opentelemetry.auto.instrumentation.mongo;

import static io.opentelemetry.auto.instrumentation.mongo.MongoClientDecorator.DECORATE;
import static io.opentelemetry.auto.instrumentation.mongo.MongoClientDecorator.TRACER;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.opentelemetry.context.Scope;
import io.opentelemetry.trace.Span;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TracingCommandListener implements CommandListener {

  private final Map<Integer, Span> spanMap = new ConcurrentHashMap<>();

  @Override
  public void commandStarted(final CommandStartedEvent event) {
    final Span span = TRACER.spanBuilder("mongo.query").startSpan();
    try (final Scope scope = TRACER.withSpan(span)) {
      DECORATE.afterStart(span);
      DECORATE.onConnection(span, event);
      if (event.getConnectionDescription() != null
          && event.getConnectionDescription() != null
          && event.getConnectionDescription().getServerAddress() != null) {
        DECORATE.onPeerConnection(
            span, event.getConnectionDescription().getServerAddress().getSocketAddress());
      }
      DECORATE.onStatement(span, event.getCommand());
      spanMap.put(event.getRequestId(), span);
    }
  }

  @Override
  public void commandSucceeded(final CommandSucceededEvent event) {
    final Span span = spanMap.remove(event.getRequestId());
    if (span != null) {
      DECORATE.beforeFinish(span);
      span.end();
    }
  }

  @Override
  public void commandFailed(final CommandFailedEvent event) {
    final Span span = spanMap.remove(event.getRequestId());
    if (span != null) {
      DECORATE.onError(span, event.getThrowable());
      DECORATE.beforeFinish(span);
      span.end();
    }
  }
}
