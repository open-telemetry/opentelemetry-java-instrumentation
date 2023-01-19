package io.opentelemetry.javaagent.instrumentation.googlehttpclient;

import com.google.api.client.http.HttpRequest;
import com.google.api.client.http.HttpResponse;

public interface RequestSender {
  HttpResponse sendRequest(HttpRequest request) throws Exception;
}
