package io.opentelemetry.javaagent.instrumentation.camunda.v7_18.processes;

import org.camunda.bpm.engine.impl.ProcessInstanceModificationBuilderImpl;

import io.opentelemetry.context.propagation.TextMapSetter;

//TODO use this or  activityinstanctiationbuildersetter ??
public class CamundaProcessInstanceModificationBuilderImpSetter
		implements TextMapSetter<ProcessInstanceModificationBuilderImpl> {

	@Override
	public void set(ProcessInstanceModificationBuilderImpl carrier, String key, String value) {
		carrier.setVariable(key, value);

	}

}
