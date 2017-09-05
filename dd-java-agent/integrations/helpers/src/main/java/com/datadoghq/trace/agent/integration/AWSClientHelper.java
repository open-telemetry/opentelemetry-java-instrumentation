package com.datadoghq.trace.agent.integration;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.RequestHandler2;
import io.opentracing.contrib.aws.TracingRequestHandler;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import org.jboss.byteman.rule.Rule;

/**
 * Patch the AWS Client during the building steps. This opentracing integration is compatible with
 * the latest release of the AWS SDK
 */
public class AWSClientHelper extends DDAgentTracingHelper<AwsClientBuilder> {

  public AWSClientHelper(Rule rule) {
    super(rule);
  }

  /**
   * Strategy: we add a tracing handler to the client when it has just been built. We intercept the
   * return value of the com.amazonaws.client.builder.AwsClientBuilder.build() method and add the
   * handler.
   *
   * @param client The fresh AWS client instance
   * @return The same instance, but patched
   * @throws Exception
   */
  protected AwsClientBuilder doPatch(AwsClientBuilder client) throws Exception {

    RequestHandler2 handler = new TracingRequestHandler(tracer);

    Field field = AwsClientBuilder.class.getDeclaredField("requestHandlers");
    field.setAccessible(true);
    List<RequestHandler2> handlers = (List<RequestHandler2>) field.get(client);

    if (handlers == null || handlers.isEmpty()) {
      handlers = Arrays.asList(handler);
    } else {
      // Check if we already added the handler
      if (!(handlers.get(0) instanceof TracingRequestHandler)) {
        handlers.add(0, handler);
      }
    }
    client.setRequestHandlers((RequestHandler2[]) handlers.toArray());
    return client;
  }
}
