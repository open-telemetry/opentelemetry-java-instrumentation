package io.opentelemetry.javaagent.instrumentation.camunda.v7_18.behavior;

import org.camunda.bpm.engine.variable.VariableMap;

import io.opentelemetry.context.propagation.TextMapSetter;

public class CamundaVariableMapSetter implements TextMapSetter<VariableMap> {

	@Override
	public void set(VariableMap carrier, String key, String value) {
		carrier.put(key, value);
	}

}
