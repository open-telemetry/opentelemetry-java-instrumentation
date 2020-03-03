package datadog.trace.instrumentation.playws1;

import datadog.trace.bootstrap.instrumentation.api.AgentSpan;
import org.reactivestreams.Publisher;
import play.shaded.ahc.org.asynchttpclient.handler.StreamedAsyncHandler;

public class StreamedAsyncHandlerWrapper extends AsyncHandlerWrapper
    implements StreamedAsyncHandler {
  private final StreamedAsyncHandler streamedDelegate;

  public StreamedAsyncHandlerWrapper(final StreamedAsyncHandler delegate, final AgentSpan span) {
    super(delegate, span);
    streamedDelegate = delegate;
  }

  @Override
  public State onStream(final Publisher publisher) {
    return streamedDelegate.onStream(publisher);
  }
}
