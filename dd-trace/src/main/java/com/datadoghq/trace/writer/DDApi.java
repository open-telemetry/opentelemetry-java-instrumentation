package com.datadoghq.trace.writer;

import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datadoghq.trace.DDBaseSpan;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The API pointing to a DD agent
 */
public class DDApi {

    private static final Logger logger = LoggerFactory.getLogger(DDApi.class.getName());

    private static final String TRACES_ENDPOINT = "/v0.3/traces";
//    private static final String SERVICES_ENDPOINT = "/v0.3/services";

    private final String tracesEndpoint;
//    private final String servicesEndpoint;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonFactory jsonFactory = objectMapper.getFactory();

    public DDApi(String host, int port) {
        this.tracesEndpoint = "http://" + host + ":" + port + TRACES_ENDPOINT;
//      this.servicesEndpoint = "http://" + host + ":" + port + SERVICES_ENDPOINT;
    }

    /**
     * Send traces to the DD agent
     *
     * @param traces the traces to be sent
     * @return the staus code returned
     */
    public boolean sendTraces(List<List<DDBaseSpan<?>>> traces) {
        int status = callPUT(tracesEndpoint, traces);
        if (status == 200) {
            logger.debug("Succesfully sent {} traces to the DD agent.", traces.size());
            return true;
        } else {
            logger.warn("Error while sending {} traces to the DD agent. Status: {}", traces.size(), status);
            return false;
        }
    }

    /**
     * PUT to an endpoint the provided JSON content
     *
     * @param endpoint
     * @param content
     * @return the status code
     */
    private int callPUT(String endpoint, Object content) {
        HttpURLConnection httpCon = null;
        try {
            URL url = new URL(endpoint);
            httpCon = (HttpURLConnection) url.openConnection();
            httpCon.setDoOutput(true);
            httpCon.setRequestMethod("PUT");
            httpCon.setRequestProperty("Content-Type", "application/json");
        } catch (Exception e) {
            logger.warn("Error thrown before PUT call to the DD agent.", e);
            return -1;
        }

        try {
            OutputStreamWriter out = new OutputStreamWriter(httpCon.getOutputStream());
            JsonGenerator jsonGen = jsonFactory.createGenerator(out);
            objectMapper.writeValue(jsonGen, content);
            jsonGen.flush();
            jsonGen.close();
            int responseCode = httpCon.getResponseCode();
            if (responseCode == 200) {
                logger.debug("Sent the payload to the DD agent.");
            } else {
                logger.warn("Could not send the payload to the DD agent. Status: {} ResponseMessage: {}", httpCon.getResponseCode(), httpCon.getResponseMessage());
            }
            return responseCode;
        } catch (Exception e) {
            logger.warn("Could not send the payload to the DD agent.", e);
            return -1;
        }
    }

}
