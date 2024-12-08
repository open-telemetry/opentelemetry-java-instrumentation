package io.opentelemetry.javaagent.instrumentation.camunda.v7_18.behavior;

import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;

import io.opentelemetry.context.propagation.TextMapGetter;

public class CamundaActivityExecutionGetter implements TextMapGetter<ActivityExecution> {

	@Override
	public Iterable<String> keys(ActivityExecution carrier) {
		return carrier.getVariableNames();
	}

	@Override
	public String get(ActivityExecution carrier, String key) {
		Object variable = carrier.getVariables().get(key);
		return variable == null ? null : variable.toString();
	}

}
