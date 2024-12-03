package io.opentelemetry.javaagent.instrumentation.camunda.v7_18.behavior;

import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;

import io.opentelemetry.context.propagation.TextMapSetter;

public class CamundaActivityExecutionLocalSetter implements TextMapSetter<ActivityExecution> {

	@Override
	public void set(ActivityExecution carrier, String key, String value) {
		carrier.setVariableLocal(key, value);
	}

}
