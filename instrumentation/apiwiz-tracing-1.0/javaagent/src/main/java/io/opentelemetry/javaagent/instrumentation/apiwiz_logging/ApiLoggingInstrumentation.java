package io.opentelemetry.javaagent.instrumentation.apiwiz_logging;

import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.returns;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.javaagent.bootstrap.context.TraceContext;
import io.opentelemetry.javaagent.bootstrap.context.TraceContextHolder;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.instrumentation.apiwiz_logging.model.ComplianceCheckDto;
import io.opentelemetry.javaagent.instrumentation.apiwiz_logging.model.Request;
import io.opentelemetry.javaagent.instrumentation.apiwiz_logging.model.Response;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;
import io.opentelemetry.javaagent.bootstrap.context.config.Config;

//import static org.apache.http.protocol.HTTP.CONTENT_TYPE;
//import static org.springframework.http.HttpMethod.POST;
//import static org.springframework.http.MediaType.APPLICATION_JSON;


public class ApiLoggingInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("org.springframework.web.servlet.DispatcherServlet");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        named("doDispatch").and(returns(void.class)),
        ApiLoggingInstrumentation.class.getName() + "$ApiLoggingAdvice"
    );
  }

  @SuppressWarnings("unused")
  public static class ApiLoggingAdvice {


    public static final String HOST = "Host";
    public static final String REFERER = "Referer";
    public static final String X_CLIENT_ID = "x-client-id";
    public static final String X_CLIENT_SECRET = "x-client-secret";

    @Advice.OnMethodEnter(suppress = Throwable.class)
    public static void onEnter(@Advice.Origin String method,
        @Advice.Local("requestTimestamp") long requestTimestamp,
        @Advice.Argument(value = 0, readOnly = false) HttpServletRequest request,
        @Advice.Argument(value = 1, readOnly = false) HttpServletResponse response) {

      requestTimestamp = System.currentTimeMillis();
      if (!(request instanceof ContentCachingRequestWrapper)) {
        request = new ContentCachingRequestWrapper(request);
      }
      if (!(response instanceof ContentCachingResponseWrapper)) {
        response = new ContentCachingResponseWrapper(response);
      }
      TraceContext.extractTraceContext(request.getHeader(Config.traceIdKey), request.getHeader(Config.spanIdKey), Config.traceIdKey, Config.spanIdKey);
    }

    @Advice.OnMethodExit(suppress = Throwable.class)
    public static void onExit(@Advice.Origin String method,
        @Advice.Local("requestTimestamp") long requestTimestamp,
        @Advice.Argument(value = 0, readOnly = false) HttpServletRequest request,
        @Advice.Argument(value = 1, readOnly = false) HttpServletResponse response) {
      try {
        ComplianceCheckDto complianceCheckDto = sendDataCompliance(request, response, requestTimestamp, System.currentTimeMillis());
        sendApiLog(complianceCheckDto);
      } catch (Exception e) {
        System.err.println("[API LOG] [ERROR] Failed to copy response body: " + e.getMessage());
      }
      System.out.println("[API LOG] Response Sent: " + method);
    }

    public static String getRequestBody(ContentCachingRequestWrapper request) {
      byte[] content = request.getContentAsByteArray();
      try {
        return content.length > 0 ? new String(content, request.getCharacterEncoding())
            : "[EMPTY BODY]";
      } catch (UnsupportedEncodingException e) {
        return "[ERROR: Unsupported Encoding]";
      }
    }

    public static ComplianceCheckDto sendDataCompliance(HttpServletRequest request,
        HttpServletResponse response, long requestTimestamp, long responseTimestamp) throws Exception {
      ContentCachingRequestWrapper requestWrapper = (ContentCachingRequestWrapper) request;
      ContentCachingResponseWrapper responseWrapper = (ContentCachingResponseWrapper) response;

      ComplianceCheckDto checkDto = new ComplianceCheckDto();
      Request requestDto = new Request();
      Map<String, Object> params = new HashMap<>();
      Enumeration<String> headerNames = requestWrapper.getHeaderNames();
      while (headerNames.hasMoreElements()) {
        String header = headerNames.nextElement();
        params.put(header, requestWrapper.getHeader(header));
      }

      TraceContextHolder traceContextHolder = TraceContext.currentTraceContext();
      params.put(Config.traceIdKey, traceContextHolder.getTraceId());
      params.put(Config.spanIdKey, traceContextHolder.getSpanId());
      params.put(Config.parentSpanIdKey, traceContextHolder.getParentSpanId());
      params.put(Config.requestTimeStampKey, requestTimestamp);
      params.put(Config.responseTimeStampKey, responseTimestamp);
      params.put(Config.gatewayTypeKey, "Apiwiz-Java-Agent");

      requestDto.setHeaderParams(params);
      requestDto.setVerb(requestWrapper.getMethod());
      requestDto.setHostname(requestWrapper.getHeader(HOST));
      requestDto.setScheme(requestWrapper.getScheme());
      requestDto.setPort(requestWrapper.getServerPort());
      requestDto.setQueryParams(new HashMap<>());
      requestDto.getQueryParams().putAll(requestWrapper.getParameterMap());
      requestDto.setPath(requestWrapper.getRequestURI());
      requestDto.setRequestBody(getRequestBody(requestWrapper));
      Response responseDto = new Response();
      responseDto.setStatusCode(String.valueOf(responseWrapper.getStatus()));
      responseDto.setResponseBody(
          new String(responseWrapper.getContentAsByteArray(), StandardCharsets.UTF_8));
      responseWrapper.copyBodyToResponse();

      responseDto.setHeaderParams(new HashMap<>());
      Collection<String> responseHeaderNames = responseWrapper.getHeaderNames();
      for (String header : responseHeaderNames) {
        responseDto.getHeaderParams().put(header, responseWrapper.getHeader(header));
      }
      checkDto.setRequest(requestDto);
      checkDto.setResponse(responseDto);
      checkDto.setServerIp(Config.serverIp);
      checkDto.setClientIp(requestWrapper.getRemoteAddr());
      checkDto.setServerHost(responseWrapper.getHeader(HOST));
      checkDto.setClientHost(requestWrapper.getHeader(REFERER));
      checkDto.setOtelTraceId(Span.current().getSpanContext().getTraceId());
      checkDto.setOtelSpanId(Span.current().getSpanContext().getSpanId());
      return checkDto;
    }

    public static void sendApiLog(ComplianceCheckDto complianceCheckDto) {
      try {
        ObjectMapper objectMapper = new ObjectMapper();
        String jsonPayload = objectMapper.writeValueAsString(complianceCheckDto);

        URL url = new URL(Config.apiwizDetectUrl); // Replace with your API URL
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("content-type", "application/json");
        conn.setRequestProperty(X_CLIENT_ID, Config.workspaceId);
        conn.setRequestProperty(X_CLIENT_SECRET, Config.apiKey);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
          os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
          os.flush();
        }

        int responseCode = conn.getResponseCode();
        try (BufferedReader in = new BufferedReader(new InputStreamReader(
            responseCode == HttpURLConnection.HTTP_OK ? conn.getInputStream()
                : conn.getErrorStream(), Charset.defaultCharset()))) {
          String inputLine;
          StringBuilder responseBody = new StringBuilder();
          while ((inputLine = in.readLine()) != null) {
            responseBody.append(inputLine);
          }
          // Print the response body
          System.out.println(
              "[API LOG] Sent log to API, Response Code: " + responseCode + " Response: "
                  + responseBody.toString());
        }

      } catch (Exception e) {
        System.err.println("[API LOG] [ERROR] Failed to send API log: " + e.getMessage());
      }
    }

  }
}
