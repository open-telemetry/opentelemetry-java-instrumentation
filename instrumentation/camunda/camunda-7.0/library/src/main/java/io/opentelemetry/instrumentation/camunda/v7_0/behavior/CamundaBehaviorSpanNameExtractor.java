package io.opentelemetry.instrumentation.camunda.v7_0.behavior;

import java.util.Arrays;

import io.opentelemetry.camunda.v7_0.common.CamundaCommonRequest;

import io.opentelemetry.instrumentation.api.instrumenter.SpanNameExtractor;

public class CamundaBehaviorSpanNameExtractor implements SpanNameExtractor<CamundaCommonRequest> {

	@Override
	public String extract(CamundaCommonRequest request) {
		String[] eventTypes = { "Start", "End" };
		String type = "Task";
		if (request.getActivityName().isPresent()
				&& Arrays.stream(eventTypes).anyMatch(request.getActivityName().get()::equals)) {
			type = "Event";

		}
		return String.format("%s %s", request.getActivityName().orElse("Plugin"), type);

	}

}
