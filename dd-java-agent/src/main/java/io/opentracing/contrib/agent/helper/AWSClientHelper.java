package io.opentracing.contrib.agent.helper;

import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.RequestHandler2;
import io.opentracing.contrib.aws.TracingRequestHandler;
import org.jboss.byteman.rule.Rule;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;


public class AWSClientHelper extends DDTracingHelper<com.amazonaws.client.builder.AwsClientBuilder> {


	private static final String HANDLERS_FIELD_NAME = "requestHandlers";

	public AWSClientHelper(Rule rule) {
		super(rule);
	}


	public AwsClientBuilder patch(AwsClientBuilder client) {
		return super.patch(client);
	}


	@Override
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