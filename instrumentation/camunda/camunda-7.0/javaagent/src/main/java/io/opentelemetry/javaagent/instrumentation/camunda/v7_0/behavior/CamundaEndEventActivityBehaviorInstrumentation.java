package io.opentelemetry.javaagent.instrumentation.camunda.v7_0.behavior;

import static io.opentelemetry.javaagent.instrumentation.camunda.v7_0.behavior.CamundaBehaviorSingletons.getInstumenter;
import static io.opentelemetry.javaagent.instrumentation.camunda.v7_0.behavior.CamundaBehaviorSingletons.getOpentelemetry;
import static io.opentelemetry.javaagent.extension.matcher.AgentElementMatchers.hasClassesNamed;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.anyOf;

import java.util.Optional;

import org.camunda.bpm.engine.impl.pvm.delegate.ActivityExecution;
import org.camunda.bpm.model.bpmn.instance.CompensateEventDefinition;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.ErrorEventDefinition;
import org.camunda.bpm.model.bpmn.instance.EventDefinition;
import org.camunda.bpm.model.bpmn.instance.TerminateEventDefinition;

import io.opentelemetry.camunda.v7_0.behavior.CamundaActivityExecutionGetter;
import io.opentelemetry.camunda.v7_0.common.CamundaCommonRequest;

import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.javaagent.bootstrap.Java8BytecodeBridge;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;

public class CamundaEndEventActivityBehaviorInstrumentation implements TypeInstrumentation {

	@Override
	public ElementMatcher<ClassLoader> classLoaderOptimization() {
		return hasClassesNamed("org.camunda.bpm.engine.impl.bpmn.behavior.TerminateEndEventActivityBehavior");
	}

	@Override
	public ElementMatcher<TypeDescription> typeMatcher() {
		return named("org.camunda.bpm.engine.impl.bpmn.behavior.TerminateEndEventActivityBehavior")
				.or(named("org.camunda.bpm.engine.impl.bpmn.behavior.NoneEndEventActivityBehavior")
						.or(named("org.camunda.bpm.engine.impl.bpmn.behavior.ErrorEndEventActivityBehavior")));
	}

	@Override
	public void transform(TypeTransformer transformer) {
		transformer.applyAdviceToMethod(ElementMatchers.isMethod().and(ElementMatchers.named("execute")),
				this.getClass().getName() + "$CamundaEndEventActivityBehaviorAdvice");
	}

	public static class CamundaEndEventActivityBehaviorAdvice {

		@Advice.OnMethodEnter(suppress = Throwable.class)
		public static void addTracingEnter(@Advice.Argument(0) ActivityExecution execution,
				@Advice.Local("request") CamundaCommonRequest request,
				@Advice.Local("otelParentScope") Scope parentScope, @Advice.Local("otelContext") Context context,
				@Advice.Local("otelScope") Scope scope) {

			if (execution == null) {
				return;
			}

			request = new CamundaCommonRequest();
			request.setProcessDefinitionId(Optional.ofNullable(execution.getProcessDefinitionId()));
			request.setProcessInstanceId(Optional.ofNullable(execution.getProcessInstanceId()));
			request.setActivityId(Optional.ofNullable(execution.getCurrentActivityId()));

			if (execution.getBpmnModelElementInstance() != null) {
				//TODO lambda does not work due to access modifier
				
				// TODO add other events execution.getBpmnModelElementInstance() instanceof
				// EndEvent
				EndEvent e = (EndEvent) execution.getBpmnModelElementInstance();

				for (EventDefinition ed : e.getEventDefinitions()) {
					if (ed instanceof TerminateEventDefinition) {
						request.setActivityName(Optional.of("End"));
					}else if(ed instanceof ErrorEventDefinition) {
						request.setActivityName(Optional.of("Error End"));
					}else if(ed instanceof CompensateEventDefinition) {
						request.setActivityName(Optional.of("Compensation End"));
					}else {
						request.setActivityName(Optional.of("End"));
					}
				}
			}

			String processInstanceId = execution.getProcessInstanceId();

			Context parentContext = getOpentelemetry().getPropagators().getTextMapPropagator()
					.extract(Java8BytecodeBridge.currentContext(), execution, new CamundaActivityExecutionGetter());

			parentScope = parentContext.makeCurrent();

			if (getInstumenter().shouldStart(Java8BytecodeBridge.currentContext(), request)) {
				context = getInstumenter().start(Java8BytecodeBridge.currentContext(), request);
				scope = context.makeCurrent();

			} 

		}

		@Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class)
		public static void closeSpan(@Advice.Local("request") CamundaCommonRequest request,
				@Advice.Local("otelParentScope") Scope parentScope, @Advice.Local("otelContext") Context context,
				@Advice.Local("otelScope") Scope scope, @Advice.Thrown Throwable throwable) {


			if (context != null && scope != null) {
				getInstumenter().end(context, request, "NA", throwable);
				scope.close();
			}

			if (parentScope != null) {
				parentScope.close();
			}

		}
	}

}
