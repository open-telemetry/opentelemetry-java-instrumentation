package io.opentelemetry.instrumentation.camunda.v7_0.behavior;

import org.camunda.bpm.engine.variable.VariableMap;

import io.opentelemetry.context.propagation.TextMapSetter;

public class CamundaVariableMapSetter implements TextMapSetter<VariableMap> {

	@Override
	public void set(VariableMap carrier, String key, String value) {
		carrier.put(key, value);
	}

}
