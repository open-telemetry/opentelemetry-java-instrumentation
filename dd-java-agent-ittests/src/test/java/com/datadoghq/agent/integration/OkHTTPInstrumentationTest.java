package com.datadoghq.agent.integration;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.OkHttpClient;
import org.junit.Test;

public class OkHTTPInstrumentationTest {

  @Test
  public void test() {
    final OkHttpClient client = new OkHttpClient().newBuilder().build();

    assertThat(client.interceptors().size()).isEqualTo(1);
    assertThat(client.interceptors().get(0).getClass().getSimpleName())
        .isEqualTo("TracingInterceptor");
  }
}
