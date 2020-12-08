package io.opentelemetry.instrumentation.awssdk.v2_2;

import io.opentelemetry.instrumentation.api.tracer.HttpClientOperation;
import software.amazon.awssdk.http.SdkHttpRequest;
import software.amazon.awssdk.http.SdkHttpResponse;

public interface AwsSdkOperation extends HttpClientOperation<SdkHttpResponse> {

  static AwsSdkOperation noop() {
    return NoopAwsSdkOperation.noop();
  }

  void inject(SdkHttpRequest.Builder request);

  void onRequest(SdkHttpRequest request);
}
