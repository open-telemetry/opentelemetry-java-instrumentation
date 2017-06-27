package io.opentracing.contrib.agent.helper;

import com.mongodb.MongoClientOptions;
import io.opentracing.contrib.mongo.TracingCommandListener;
import org.jboss.byteman.rule.Rule;

/**
 * Patch the Mongo builder before constructing the final client
 */
public class MongoHelper extends DDAgentTracingHelper<MongoClientOptions.Builder> {


	public MongoHelper(Rule rule) {
		super(rule);
	}

	/**
	 * Strategy: Just before com.mongodb.MongoClientOptions$Builder.build() method is called, we add a new command listener
	 * in charge of the tracing.
	 *
	 * @param builder The builder instance
	 * @return The same builder instance with a new tracing command listener that will be use for the client construction
	 * @throws Exception
	 */
	protected MongoClientOptions.Builder doPatch(MongoClientOptions.Builder builder) throws Exception {


		TracingCommandListener listener = new TracingCommandListener(tracer);
		builder.addCommandListener(listener);

		setState(builder, 1);

		return builder;

	}
}
