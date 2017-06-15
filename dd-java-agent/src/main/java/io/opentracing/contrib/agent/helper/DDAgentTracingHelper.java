package io.opentracing.contrib.agent.helper;

import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;
import io.opentracing.contrib.agent.OpenTracingHelper;
import org.jboss.byteman.rule.Rule;

import java.util.logging.Level;
import java.util.logging.Logger;


public abstract class DDAgentTracingHelper<E> extends OpenTracingHelper {

	protected static Tracer tracer;
	private static final Logger LOGGER = Logger.getLogger(DDAgentTracingHelper.class.getCanonicalName());

	public DDAgentTracingHelper(Rule rule) {
		super(rule);
		try {
			tracer = getTracer() != null ? getTracer() : NoopTracerFactory.create();
		} catch (Exception e) {
			tracer = NoopTracerFactory.create();
			warning("Failed to retrieve the tracer, using a NoopTracer: " + e.getMessage());
			logStackTrace(e.getMessage(), e);
		}
	}

	public E patch(E args) {

		info("Try to patch " + args.getClass().getName());
		E patched;
		try {
			patched = doPatch(args);
			info(args.getClass().getName() + " patched");
		} catch (Throwable e) {
			warning("Failed to patch" + args.getClass().getName() + ", reason: " + e.getMessage());
			logStackTrace(e.getMessage(), e);
			patched = args;
		}
		return patched;

	}

	abstract protected E doPatch(E input) throws Exception;

	protected void warning(String message) {
		log(Level.WARNING, message);
	}

	protected void info(String message) {
		log(Level.INFO, message);
	}

	protected void logStackTrace(String message, Throwable th) {
		LOGGER.log(Level.FINE, message, th);
	}

	private void log(Level level, String message) {
		LOGGER.log(level, String.format("%s - %s", getClass().getSimpleName(), message));
	}

}
