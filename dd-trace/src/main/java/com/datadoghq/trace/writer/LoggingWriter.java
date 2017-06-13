package com.datadoghq.trace.writer;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datadoghq.trace.DDBaseSpan;
import com.google.auto.service.AutoService;

@AutoService(Writer.class)
public class LoggingWriter implements Writer{

	private static final Logger logger = LoggerFactory.getLogger(LoggingWriter.class.getName());
	
	@Override
	public void write(List<DDBaseSpan<?>> trace) {
		logger.info("write(trace): {}", trace);
	}

	@Override
	public void close() {
		logger.info("close()");
	}

	@Override
	public void start() {
		logger.info("start()");
	}
}
