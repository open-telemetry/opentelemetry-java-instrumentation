package com.datadoghq.trace.propagation;

import com.datadoghq.trace.DDSpanContext;

import io.opentracing.propagation.TextMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A codec designed for HTTP transport via headers
 */
public class HTTPCodec implements Codec<TextMap> {

    private static final String OT_PREFIX = "ot-tracer-";
    private static final String OT_BAGGAGE_PREFIX = "ot-baggage-";
    private static final String TRACE_ID_KEY = OT_PREFIX + "traceid";
    private static final String SPAN_ID_KEY = OT_PREFIX + "spanid";

    private static final Logger logger = LoggerFactory.getLogger(HTTPCodec.class);

    @Override
    public void inject(DDSpanContext context, TextMap carrier) {

        carrier.put(TRACE_ID_KEY, String.valueOf(context.getTraceId()));
        carrier.put(SPAN_ID_KEY, String.valueOf(context.getSpanId()));

        for (Map.Entry<String, String> entry : context.baggageItems()) {
            carrier.put(OT_BAGGAGE_PREFIX + entry.getKey(), encode(entry.getValue()));
        }
    }

    @Override
    public DDSpanContext extract(TextMap carrier) {

        Map<String, String> baggage = Collections.emptyMap();
        Long traceId = 0L;
        Long spanId = 0L;

        for (Map.Entry<String, String> entry : carrier) {

            if (entry.getKey().equals(TRACE_ID_KEY)) {
                traceId = Long.parseLong(entry.getValue());
            } else if (entry.getKey().equals(SPAN_ID_KEY)) {
                spanId = Long.parseLong(entry.getValue());
            } else if (entry.getKey().startsWith(OT_BAGGAGE_PREFIX)) {
                if (baggage.isEmpty()) {
                    baggage = new HashMap<String, String>();
                }
                baggage.put(entry.getKey(), decode(entry.getValue()));
            }
        }
        DDSpanContext context = null;
        if (traceId != 0L) {

            context = new DDSpanContext(
                    traceId,
                    spanId,
                    0L,
                    null,
                    null,
                    null,
                    baggage,
                    false,
                    null,
                    null,
                    null,
                    null);

            logger.debug("{} - Parent context extracted", context);
        }

        return context;
    }


    private String encode(String value) {
        String encoded = value;
        try {
            encoded = URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.info("Failed to encode value - {}", value);
        }
        return encoded;
    }

    private String decode(String value) {
        String decoded = value;
        try {
            decoded = URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            logger.info("Failed to decode value - {}", value);
        }
        return decoded;
    }

}
