package com.datadoghq.trace.agent.integration;

import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;
import io.opentracing.contrib.agent.OpenTracingHelper;
import org.jboss.byteman.rule.Rule;

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class provides helpfull stuff in order to easy patch object using Byteman rules
 *
 * @param <T> The type of the object to patch
 */
public abstract class DDAgentTracingHelper<T> extends OpenTracingHelper {

	private static final Logger LOGGER = Logger.getLogger(DDAgentTracingHelper.class.getCanonicalName());

	/**
	 * The current instance of the tracer. If something goes wrong during the resolution,
	 * we provides a NoopTracer.
	 */
	protected final Tracer tracer;

	DDAgentTracingHelper(Rule rule) {
		super(rule);
		Tracer tracerResolved;
		try {
			tracerResolved = getTracer();
			tracerResolved = tracerResolved == null ? NoopTracerFactory.create() : tracerResolved;
		} catch (Exception e) {
			tracerResolved = NoopTracerFactory.create();
			warning("Failed to retrieve the tracer, using a NoopTracer instead: " + e.getMessage());
			logStackTrace(e.getMessage(), e);
		}
		tracer = tracerResolved;
	}


	/**
	 * This method takes an object and applies some mutation in order to add tracing capabilities.
	 * This method should never return any Exception in order to not stop the app traced.
	 * <p>
	 * This method should be defined as final, but something Byteman need to define this one with the explicit
	 * type (i.e. without using generic), so this is why we don't use final here.
	 *
	 * @param args The object to patch, the type is defined by the subclass instantiation
	 * @return The object patched
	 */
	public T patch(T args) {

		if (args == null) {
			info("Skipping " + rule.getName() + "' rule because the input arg is null");
			return args;
		}

		String className = args.getClass().getName();
		info("Try to patch " + className);

		T patched;
		try {
			patched = doPatch(args);
			info(className + " patched");
		} catch (Throwable e) {
			warning("Failed to patch" + className + ", reason: " + e.getMessage());
			logStackTrace(e.getMessage(), e);
			patched = args;
		}
		return patched;
	}

	/**
	 * The current implementation of the patch
	 *
	 * @param obj the object to patch
	 * @return the object patched
	 * @throws Exception The exceptions are managed directly to the patch method
	 */
	abstract protected T doPatch(T obj) throws Exception;


	/**
	 * Simple wrapper to emit a warning
	 *
	 * @param message the message to log as a warning
	 */
	protected void warning(String message) {
		log(Level.WARNING, message);
	}

	/**
	 * Simple wrapper to emit an info
	 *
	 * @param message the message to log as an info
	 */
	protected void info(String message) {
		log(Level.INFO, message);
	}

	/**
	 * Simple wrapper to emit the corresponding stacktrace. To not warn the user, we log the stack as a debug info.
	 * By default, the stack traces are noit shown in the log.
	 *
	 * @param message the stacktrace to log as a debug
	 */
	protected void logStackTrace(String message, Throwable th) {
		LOGGER.log(Level.FINE, message, th);
	}

	private void log(Level level, String message) {
		LOGGER.log(level, String.format("%s - %s", getClass().getSimpleName(), message));
	}

}
