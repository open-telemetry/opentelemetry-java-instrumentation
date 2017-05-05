package com.datadoghq.trace.writer;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.opentracing.Span;

public class LoggingWritter implements Writer{

	private static final Logger logger = LoggerFactory.getLogger(LoggingWritter.class.getName());
	
	@Override
	public void write(List<Span> trace) {
		logger.info("write(trace): {}", trace);
	}

	@Override
	public void close() {
		logger.info("close()");
	}
}
