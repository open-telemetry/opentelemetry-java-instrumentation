package io.opentelemetry.javaagent.instrumentation.camunda.v7_18.processes;

import io.opentelemetry.javaagent.instrumentation.camunda.v7_18.common.CamundaCommonRequest;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class CamundaProcessSpanNameExtractor implements SpanNameExtractor<CamundaCommonRequest> {

	@Override
	public String extract(CamundaCommonRequest request) {
		return request.getProcessDefinitionKey().get();
	}

}
