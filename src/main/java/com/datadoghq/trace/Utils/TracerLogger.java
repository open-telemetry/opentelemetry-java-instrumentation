package com.datadoghq.trace.Utils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TracerLogger {

    private Logger logger = LoggerFactory.getLogger("com.datadoghq.trace");

    private final String startNewSpan = "Starting new span - %s [%s]";

    public void startNewSpan(String operationName, long spanId) {

        if (!logger.isTraceEnabled()) return;
        logger.trace(String.format(startNewSpan, operationName, String.valueOf(spanId)));
    }

}
