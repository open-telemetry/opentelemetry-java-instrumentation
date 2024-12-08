package io.opentelemetry.javaagent.instrumentation.camunda.v7_18.task;

import io.opentelemetry.javaagent.instrumentation.camunda.v7_18.common.CamundaCommonRequest;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class CamundaTaskSpanNameExtractor implements SpanNameExtractor<CamundaCommonRequest> {

	@Override
	public String extract(CamundaCommonRequest request) {
		return String.format("%s Topic", request.getTopicName().get());
	}

}
