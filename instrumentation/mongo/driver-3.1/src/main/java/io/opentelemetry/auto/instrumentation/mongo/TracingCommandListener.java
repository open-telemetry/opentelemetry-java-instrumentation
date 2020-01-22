package io.opentelemetry.auto.instrumentation.mongo;

import static io.opentelemetry.auto.instrumentation.api.AgentTracer.activateSpan;
import static io.opentelemetry.auto.instrumentation.api.AgentTracer.startSpan;
import static io.opentelemetry.auto.instrumentation.mongo.MongoClientDecorator.DECORATE;

import com.mongodb.event.CommandFailedEvent;
import com.mongodb.event.CommandListener;
import com.mongodb.event.CommandStartedEvent;
import com.mongodb.event.CommandSucceededEvent;
import io.opentelemetry.auto.instrumentation.api.AgentScope;
import io.opentelemetry.auto.instrumentation.api.AgentSpan;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TracingCommandListener implements CommandListener {

  private final Map<Integer, AgentSpan> spanMap = new ConcurrentHashMap<>();

  @Override
  public void commandStarted(final CommandStartedEvent event) {
    final AgentSpan span = startSpan("mongo.query");
    try (final AgentScope scope = activateSpan(span, false)) {
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
    final AgentSpan span = spanMap.remove(event.getRequestId());
    if (span != null) {
      DECORATE.beforeFinish(span);
      span.finish();
    }
  }

  @Override
  public void commandFailed(final CommandFailedEvent event) {
    final AgentSpan span = spanMap.remove(event.getRequestId());
    if (span != null) {
      DECORATE.onError(span, event.getThrowable());
      DECORATE.beforeFinish(span);
      span.finish();
    }
  }
}
