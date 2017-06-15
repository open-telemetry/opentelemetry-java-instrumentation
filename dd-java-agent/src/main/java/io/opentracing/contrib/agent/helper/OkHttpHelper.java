package io.opentracing.contrib.agent.helper;

import io.opentracing.contrib.okhttp3.TracingInterceptor;
import okhttp3.OkHttpClient;
import org.jboss.byteman.rule.Rule;

import java.util.Collections;

import static io.opentracing.contrib.okhttp3.OkHttpClientSpanDecorator.STANDARD_TAGS;

/**
 * Created by gpolaert on 6/15/17.
 */
public class OkHttpHelper extends DDTracingHelper<OkHttpClient.Builder> {


	public OkHttpHelper(Rule rule) {
		super(rule);
	}

	@Override
	protected OkHttpClient.Builder doPatch(OkHttpClient.Builder builder) throws Exception {
		TracingInterceptor interceptor = new TracingInterceptor(tracer, Collections.singletonList(STANDARD_TAGS));
		builder.addInterceptor(interceptor);
		builder.addNetworkInterceptor(interceptor);

		return builder;
	}
}