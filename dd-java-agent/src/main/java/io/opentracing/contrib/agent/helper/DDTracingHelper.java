package io.opentracing.contrib.agent.helper;

import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;
import io.opentracing.contrib.agent.OpenTracingHelper;
import org.jboss.byteman.rule.Rule;

import java.lang.reflect.InvocationTargetException;


public abstract class DDTracingHelper<E> extends OpenTracingHelper {


	protected final Tracer tracer;
	private final static String LOG_PREFIX = "[DD-JAVA-AGENT]";


	public DDTracingHelper(Rule rule) {
		super(rule);
		Tracer activeTracer;
		try {
			activeTracer = getTracer();
		} catch (Exception e) {
			activeTracer = NoopTracerFactory.create();
			error("Failed to retrieve the tracer, using a NoopTracer: " + e.getMessage());
			errTraceException(e);
		}
		tracer = activeTracer;
	}


	public boolean debug(String message) {
		return super.debug(String.format("%s - %s - %s", LOG_PREFIX, getClass().getCanonicalName(), message));
	}

	public boolean error(String message) {
		return OpenTracingHelper.err(String.format("%s - %s - %s", LOG_PREFIX, getClass().getCanonicalName(), message));
	}

	public E patch(E args) {

		debug("Try to patch " + args.getClass().getName());
		E patched;
		try {
			patched = doPatch(args);
			debug(args.getClass().getName() + " patched");
		} catch (ClassNotFoundException | NoSuchMethodException | InstantiationException | IllegalAccessException | InvocationTargetException e) {
			error("Your " + args.getClass().getName() + "seems to be not compatible with the current integration, integration disabled, reason: " + e.getMessage());
			errTraceException(e);
			patched = args;
		} catch (Exception e) {
			error("Failed to patch" + args.getClass().getName() + ", reason: " + e.getMessage());
			errTraceException(e);
			patched = args;
		}
		return patched;

	}

	abstract protected E doPatch(E input) throws Exception;

}
