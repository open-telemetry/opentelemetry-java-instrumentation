package io.opentelemetry.javaagent.instrumentation.camunda.v7_18.jobs;

import org.camunda.bpm.engine.impl.persistence.entity.ExecutionEntity;

import io.opentelemetry.javaagent.instrumentation.camunda.v7_18.common.CamundaCommonRequest;
import io.opentelemetry.javaagent.instrumentation.camunda.v7_18.common.CamundaVariableAttributeExtractor;
import io.opentelemetry.javaagent.instrumentation.camunda.v7_18.jobs.CamundaJobSpanNameExtractor;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.api.instrumenter.Instrumenter;
import io.opentelemetry.instrumentation.api.instrumenter.InstrumenterBuilder;

public class CamundaJobSingletons {

	private static final Instrumenter<CamundaCommonRequest, String> instrumenter;

	private static final OpenTelemetry opentelemetry;

	static {

		opentelemetry = GlobalOpenTelemetry.get();

		InstrumenterBuilder<CamundaCommonRequest, String> builder = Instrumenter
				.<CamundaCommonRequest, String>builder(opentelemetry, "io.opentelemetry.camunda-job",
						new CamundaJobSpanNameExtractor())
				.addAttributesExtractor(new CamundaVariableAttributeExtractor());

		instrumenter = builder.buildInstrumenter();
	}

	public static OpenTelemetry getOpentelemetry() {
		return opentelemetry;
	}

	public static Instrumenter<CamundaCommonRequest, String> getInstumenter() {
		return instrumenter;
	}


}
