package io.opentelemetry.instrumentation.camunda.v7_0.task;

import io.opentelemetry.instrumentation.camunda.v7_0.common.CamundaCommonRequest;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class CamundaTaskSpanNameExtractor implements SpanNameExtractor<CamundaCommonRequest> {

	@Override
	public String extract(CamundaCommonRequest request) {
		return String.format("%s Topic", request.getTopicName().get());
	}

}
