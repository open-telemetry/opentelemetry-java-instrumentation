/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.webflux.client;

import static io.opentelemetry.instrumentation.testing.junit.http.AbstractHttpClientTest.CONNECTION_TIMEOUT;

import io.netty.channel.ChannelOption;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.function.Consumer;
import org.springframework.http.client.reactive.ClientHttpConnector;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.ipc.netty.http.client.HttpClientOptions;
import reactor.ipc.netty.resources.PoolResources;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.LoopResources;

final class ClientHttpConnectorFactory {

  static ClientHttpConnector create() {
    return isOldVersion() ? createOldVersionConnector(false) : createNewVersionConnector(false);
  }

  static ClientHttpConnector createSingleConnection() {
    return isOldVersion() ? createOldVersionConnector(true) : createNewVersionConnector(true);
  }

  private static boolean isOldVersion() {
    try {
      Class.forName("reactor.netty.http.client.HttpClient");
      return false;
    } catch (ClassNotFoundException exception) {
      return true;
    }
  }

  private static ReactorClientHttpConnector createOldVersionConnector(
      boolean limitToSingleConnection) {
    try {
      // the object & cast hack tricks junit into not loading the HttpClientOptions.Builder class
      // junit scans all classes, all methods for tests, and if the builder was referenced as the
      // lambda parameter it'd fail to load the class during the latestDepTest run
      Consumer<Object> clientOptionsConfigurer =
          clientOptions -> {
            HttpClientOptions.Builder builder = (HttpClientOptions.Builder) clientOptions;
            builder.option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECTION_TIMEOUT.toMillis());
            if (limitToSingleConnection) {
              builder.poolResources(PoolResources.fixed("pool", 1));
            }
          };
      Constructor<ReactorClientHttpConnector> constructor =
          ReactorClientHttpConnector.class.getConstructor(Consumer.class);
      return constructor.newInstance(clientOptionsConfigurer);
    } catch (NoSuchMethodException
        | InvocationTargetException
        | InstantiationException
        | IllegalAccessException e) {
      throw new LinkageError("Did not find suitable constructor", e);
    }
  }

  private static ClientHttpConnector createNewVersionConnector(boolean limitToSingleConnection) {
    try {
      HttpClient httpClient =
          HttpClient.create()
              .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) CONNECTION_TIMEOUT.toMillis());
      if (limitToSingleConnection) {
        httpClient = httpClient.runOn(LoopResources.create("pool", 1, true));
      }
      Constructor<ReactorClientHttpConnector> constructor =
          ReactorClientHttpConnector.class.getConstructor(HttpClient.class);
      return constructor.newInstance(httpClient);
    } catch (NoSuchMethodException
        | InvocationTargetException
        | InstantiationException
        | IllegalAccessException e) {
      throw new LinkageError("Did not find suitable constructor", e);
    }
  }

  private ClientHttpConnectorFactory() {}
}
