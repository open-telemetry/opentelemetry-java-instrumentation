package io.opentelemetry.javaagent.instrumentation.camunda.v7_18.processes;

import org.camunda.bpm.engine.runtime.ActivityInstantiationBuilder;

import io.opentelemetry.context.propagation.TextMapSetter;

//TODO Bound it to ProcessInstanceModificationBuilder ??
public class CamundaActivityInstantiationBuilderSetter implements TextMapSetter<ActivityInstantiationBuilder<?>> {

	@Override
	public void set(ActivityInstantiationBuilder<?> carrier, String key, String value) {
		carrier.setVariable(key, value);

	}

}
