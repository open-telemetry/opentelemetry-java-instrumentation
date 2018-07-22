package datadog.trace.agent.test

import okhttp3.OkHttpClient

import java.util.concurrent.TimeUnit

class OkHttpUtils {

  static clientBuilder() {
    new OkHttpClient.Builder()
      .connectTimeout(1, TimeUnit.MINUTES)
      .writeTimeout(1, TimeUnit.MINUTES)
      .readTimeout(1, TimeUnit.MINUTES)
  }

  static client() {
    clientBuilder().build()
  }
}
