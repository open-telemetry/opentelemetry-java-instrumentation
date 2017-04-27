package com.datadoghq.trace.writer.impl;

import java.util.List;

import com.datadoghq.trace.impl.DDSpan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datadoghq.trace.Writer;

import io.opentracing.Span;

public class LoggingWritter implements Writer{

	protected static final Logger logger = LoggerFactory.getLogger(LoggingWritter.class.getName());
	
	@Override
	public void write(List<DDSpan> trace) {
		logger.info("write(trace): "+trace);
	}

	@Override
	public void close() {
		logger.info("close()");
	}
}
