package io.opentelemetry.javaagent.instrumentation.camunda.v7_18.jobs;

import com.att.sre.otel.instrumentation.camunda.v7_18.common.CamundaCommonRequest;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class CamundaJobSpanNameExtractor implements SpanNameExtractor<CamundaCommonRequest> {

	@Override
	public String extract(CamundaCommonRequest request) {
		return String.format("%s AsyncContinuation", request.getActivityName().get());
	}

}
