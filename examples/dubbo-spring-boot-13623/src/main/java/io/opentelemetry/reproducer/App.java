/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.reproducer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import org.apache.dubbo.common.extension.ExtensionLoader;
import org.apache.dubbo.config.ApplicationConfig;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ReferenceConfig;
import org.apache.dubbo.config.RegistryConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.rpc.Filter;

public final class App {
  private static final String FILTER_PACKAGE =
      "io.opentelemetry.javaagent.instrumentation.apachedubbo.v2_7.";
  private static final String HOST = "localhost.";

  public static void main(String[] args) {
    int status = 0;
    ClassLoader loader = App.class.getClassLoader();
    System.out.println("JAVA " + System.getProperty("java.runtime.version"));
    System.out.println("APPLICATION_LOADER " + loader);
    for (ClassLoader parent = loader.getParent(); parent != null; parent = parent.getParent()) {
      System.out.println("PARENT_LOADER " + parent);
    }
    System.out.println("TCCL " + Thread.currentThread().getContextClassLoader());
    try {
      request();
    } catch (Throwable t) {
      status = 1;
      System.out.println("RPC_OR_STARTUP_FAILED");
      t.printStackTrace(System.out);
    } finally {
      // Probe only after the real request, so diagnostics cannot pre-load SPI filters for it.
      try {
        if (!diagnose(loader) && status == 0) {
          status = 2;
        }
      } catch (Throwable t) {
        if (status == 0) {
          status = 3;
        }
        System.out.println("DIAGNOSTICS_FAILED");
        t.printStackTrace(System.out);
      }
    }
    System.out.println("RESULT exit=" + status);
    // Dubbo owns background threads; normal JVM shutdown also flushes the agent's exporter.
    System.exit(status);
  }

  private static void request() throws Exception {
    ApplicationConfig application = new ApplicationConfig("issue-13623");
    application.setQosEnable(false);
    ProtocolConfig protocol = new ProtocolConfig("dubbo");
    // Dubbo 2.7 replaces literal localhost/127.* with a wildcard bind, but not this FQDN.
    for (InetAddress address : InetAddress.getAllByName(HOST)) {
      if (!address.isLoopbackAddress()) {
        throw new IllegalStateException(HOST + " must resolve only to loopback: " + address);
      }
    }
    protocol.setHost(HOST);
    try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getByName(HOST))) {
      protocol.setPort(socket.getLocalPort());
    }
    ServiceConfig<EchoService> service = new ServiceConfig<>();
    ReferenceConfig<EchoService> reference = new ReferenceConfig<>();
    try {
      service.setApplication(application);
      service.setRegistry(new RegistryConfig(RegistryConfig.NO_AVAILABLE));
      service.setProtocol(protocol);
      service.setInterface(EchoService.class);
      service.setRef(new EchoServiceImpl());
      System.out.println("STAGE export");
      service.export();

      reference.setApplication(application);
      reference.setInterface(EchoService.class);
      reference.setUrl("dubbo://" + HOST + ":" + protocol.getPort());
      reference.setInjvm(false);
      reference.setRetries(0);
      reference.setTimeout(5000);
      System.out.println("STAGE refer");
      EchoService client = reference.get();
      System.out.println("STAGE request");
      String response = client.echo("hello");
      if (!"echo:hello".equals(response)) {
        throw new IllegalStateException("Unexpected response: " + response);
      }
      System.out.println("RPC_OK " + response);
    } finally {
      reference.destroy();
      service.unexport();
    }
  }

  private static boolean diagnose(ClassLoader loader) throws Exception {
    System.out.println("EXTENSION_LOADER " + ExtensionLoader.class.getClassLoader());
    System.out.println("FILTER_LOADER " + Filter.class.getClassLoader());
    for (String prefix : new String[] {"META-INF/services/", "META-INF/dubbo/internal/"}) {
      String path = prefix + Filter.class.getName();
      System.out.println("RESOURCE_ONE " + path + " " + loader.getResource(path));
      Enumeration<URL> resources = loader.getResources(path);
      int count = 0;
      while (resources.hasMoreElements()) {
        URL resource = resources.nextElement();
        count++;
        System.out.println("RESOURCE_ALL " + path + " " + resource);
        try (BufferedReader reader =
            new BufferedReader(
                new InputStreamReader(resource.openStream(), StandardCharsets.UTF_8))) {
          String line;
          while ((line = reader.readLine()) != null) {
            if (line.contains("OpenTelemetry")) {
              System.out.println("SPI_ENTRY " + line);
            }
          }
        }
      }
      System.out.println("RESOURCE_COUNT " + path + " " + count);
    }
    ExtensionLoader<Filter> extensions = ExtensionLoader.getExtensionLoader(Filter.class);
    boolean client = false;
    boolean server = false;
    System.out.println("SUPPORTED_FILTERS " + extensions.getSupportedExtensions());
    for (String name : extensions.getSupportedExtensions()) {
      if (name.toLowerCase(java.util.Locale.ROOT).contains("opentelemetry")) {
        try {
          Class<?> type = extensions.getExtension(name).getClass();
          System.out.println("DISCOVERED_FILTER " + name + " " + type.getName());
          client |= type.getSimpleName().equals("OpenTelemetryClientFilter");
          server |= type.getSimpleName().equals("OpenTelemetryServerFilter");
        } catch (Throwable t) {
          System.out.println("FILTER_INSTANTIATION_FAILED " + name);
          t.printStackTrace(System.out);
        }
      }
    }
    for (String name : new String[] {"OpenTelemetryClientFilter", "OpenTelemetryServerFilter"}) {
      try {
        Class<?> type = Class.forName(FILTER_PACKAGE + name, false, loader);
        System.out.println("HELPER_LOAD_OK " + name + " " + type.getClassLoader());
      } catch (Throwable t) {
        System.out.println("HELPER_LOAD_FAILED " + name);
        t.printStackTrace(System.out);
      }
    }
    System.out.println("FILTERS client=" + client + " server=" + server);
    return client && server;
  }

  public interface EchoService {
    String echo(String value);
  }

  public static final class EchoServiceImpl implements EchoService {
    @Override
    public String echo(String value) {
      return "echo:" + value;
    }
  }

  private App() {}
}
