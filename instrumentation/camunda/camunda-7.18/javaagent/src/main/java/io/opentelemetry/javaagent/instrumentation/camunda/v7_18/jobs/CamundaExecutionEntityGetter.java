package io.opentelemetry.javaagent.instrumentation.camunda.v7_18.jobs;

import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;

import io.opentelemetry.context.propagation.TextMapGetter;

public class CamundaExecutionEntityGetter implements TextMapGetter<ExecutionEntity> {

	@Override
	public Iterable<String> keys(ExecutionEntity carrier) {
		return carrier.getVariableNames();
	}

	@Override
	public String get(ExecutionEntity carrier, String key) {
		Object variable = carrier.getVariables().get(key);
		return variable == null ? null : variable.toString();
	}

}
