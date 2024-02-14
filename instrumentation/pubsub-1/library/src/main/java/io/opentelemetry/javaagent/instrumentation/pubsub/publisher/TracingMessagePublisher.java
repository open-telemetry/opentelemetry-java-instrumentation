package io.opentelemetry.javaagent.instrumentation.pubsub.publisher;

import com.google.api.core.ApiFuture;
import com.google.api.core.SettableApiFuture;
import com.google.api.gax.rpc.ApiCallContext;
import com.google.api.gax.rpc.UnaryCallable;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.pubsub.v1.PublishRequest;
import com.google.pubsub.v1.PublishResponse;
import io.opentelemetry.javaagent.instrumentation.pubsub.PubsubUtils;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class TracingMessagePublisher extends UnaryCallable<PublishRequest, PublishResponse> {
  private static final Logger logger = Logger.getLogger(TracingMessagePublisher.class.getName());
  private final UnaryCallable<PublishRequest, PublishResponse> originalCallable;

  public TracingMessagePublisher(UnaryCallable<PublishRequest, PublishResponse> originalCallable) {
    this.originalCallable = originalCallable;
  }

  List<Context> start(Context parentContext, List<PublishMessageHelper.Request> requests) {
    if (PublishMessageHelper.INSTRUMENTER.shouldStart(parentContext, requests.get(0))) {
      return requests.stream()
          .map(req -> PublishMessageHelper.INSTRUMENTER.start(parentContext, req))
          .collect(Collectors.toList());
    } else {
      return Collections.emptyList();
    }
  }

  void end(List<PublishMessageHelper.Request> requests, List<Context> contexts, PublishResponse response, Throwable error) {
    for (int i = 0; i < requests.size(); ++i) {
      Context context = contexts.get(i);
      if (context != null) {
        PublishMessageHelper.INSTRUMENTER.end(context, requests.get(i), response, error);
      }
    }
  }

  @Override
  public ApiFuture<PublishResponse> futureCall(PublishRequest req, ApiCallContext apiCallContext) {
    if (req.getMessagesList().isEmpty()) {
      return originalCallable.futureCall(req, apiCallContext);
    }

    Context parentContext = Context.current();
    String topicName = PubsubUtils.getResourceName(req.getTopic());
    String topicFullResourceName = PubsubUtils.getFullResourceName(req.getTopic());

    // Start a span for each message
    List<PublishMessageHelper.Request> msgRequests = PublishMessageHelper.deconstructRequest(topicName, topicFullResourceName, req.getMessagesList());
    List<Context> msgContexts = start(parentContext, msgRequests);
    PublishRequest modifiedRequest = PublishMessageHelper.reconstructRequest(req, msgRequests);

    // Start a span for the batch, and make it current
    PublishBatchHelper.Request batchRequest = new PublishBatchHelper.Request(topicName, topicFullResourceName, req.getMessagesCount(), msgContexts);
    Context batchContext = PublishBatchHelper.INSTRUMENTER.shouldStart(parentContext, batchRequest) ?
            PublishBatchHelper.INSTRUMENTER.start(parentContext, batchRequest) :
            null;

    // Create a new future that will finish once the original callback AND the spans have ended
    SettableApiFuture<PublishResponse> returnedFuture = SettableApiFuture.create();

    try (Scope scope = batchContext == null ? Scope.noop() : batchContext.makeCurrent()) {

      // Call the original callable, and add callback to run when the future finishes
      ApiFuture<PublishResponse> future = originalCallable.futureCall(modifiedRequest, apiCallContext);
      future.addListener(() -> {
        PublishResponse response = null;
        Throwable error = null;
        try {
          response = future.get();
        } catch (RuntimeException|InterruptedException e) {
          error = e;
        } catch (ExecutionException e) {
          error = e.getCause();
        }

        try {
          if (!msgContexts.isEmpty()) {
            end(msgRequests, msgContexts, response, error);
          }
        } catch (Throwable t) {
          logger.log(Level.WARNING, "Error ending pubsub publish message spans", t);
        }

        if (batchContext != null) {
          try {
            PublishBatchHelper.INSTRUMENTER.end(batchContext, batchRequest, null, error);
          } catch (Throwable t) {
            logger.log(Level.WARNING, "Error ending pubsub publish batch span", t);
          }
        }

        if (error == null) {
          returnedFuture.set(response);
        } else {
          returnedFuture.setException(error);
        }
      }, MoreExecutors.directExecutor());
    }
    return returnedFuture;
  }
}
