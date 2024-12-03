package io.opentelemetry.javaagent.instrumentation.camunda.v7_18.task;

import org.camunda.bpm.client.task.ExternalTask;

import io.opentelemetry.context.propagation.TextMapGetter;

public class CamundaExternalTaskGetter implements TextMapGetter<ExternalTask> {

	@Override
	public Iterable<String> keys(ExternalTask carrier) {
		return carrier.getAllVariables().keySet();
	}

	@Override
	public String get(ExternalTask carrier, String key) {
		Object variable = carrier.getAllVariables().get(key);
		return variable == null ? null : variable.toString();
	}

}
