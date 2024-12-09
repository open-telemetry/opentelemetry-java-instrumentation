package io.opentelemetry.instrumentation.camunda.v7_0.processes;

import io.opentelemetry.instrumentation.camunda.v7_0.common.CamundaCommonRequest;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class CamundaProcessSpanNameExtractor implements SpanNameExtractor<CamundaCommonRequest> {

	@Override
	public String extract(CamundaCommonRequest request) {
		return request.getProcessDefinitionKey().get();
	}

}
