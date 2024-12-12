/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.instrumentation.spring.autoconfigure.internal.properties;

import java.util.Collections;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * This class is internal and is hence not for public use. Its APIs are unstable and can change at
 * any time.
 */
// yaml lists only work if you create a @ConfigurationProperties object
@ConfigurationProperties(prefix = "otel")
public final class OtelSpringProperties {

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static final class Java {
    /**
     * This class is internal and is hence not for public use. Its APIs are unstable and can change
     * at any time.
     */
    public static final class Enabled {
      /**
       * This class is internal and is hence not for public use. Its APIs are unstable and can
       * change at any time.
       */
      public static final class Resource {
        private List<String> providers = Collections.emptyList();

        public List<String> getProviders() {
          return providers;
        }

        public void setProviders(List<String> providers) {
          this.providers = providers;
        }
      }

      private Enabled.Resource resource = new Enabled.Resource();

      public Enabled.Resource getResource() {
        return resource;
      }

      public void setResource(Enabled.Resource resource) {
        this.resource = resource;
      }
    }

    /**
     * This class is internal and is hence not for public use. Its APIs are unstable and can change
     * at any time.
     */
    public static final class Disabled {
      /**
       * This class is internal and is hence not for public use. Its APIs are unstable and can
       * change at any time.
       */
      public static final class Resource {
        private List<String> providers = Collections.emptyList();

        public List<String> getProviders() {
          return providers;
        }

        public void setProviders(List<String> providers) {
          this.providers = providers;
        }
      }

      private Disabled.Resource resource = new Disabled.Resource();

      public Disabled.Resource getResource() {
        return resource;
      }

      public void setResource(Disabled.Resource resource) {
        this.resource = resource;
      }
    }

    private Enabled enabled = new Enabled();
    private Java.Disabled disabled = new Java.Disabled();

    public Enabled getEnabled() {
      return enabled;
    }

    public void setEnabled(Enabled enabled) {
      this.enabled = enabled;
    }

    public Java.Disabled getDisabled() {
      return disabled;
    }

    public void setDisabled(Java.Disabled disabled) {
      this.disabled = disabled;
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static final class Experimental {
    /**
     * This class is internal and is hence not for public use. Its APIs are unstable and can change
     * at any time.
     */
    public static final class Metrics {
      /**
       * This class is internal and is hence not for public use. Its APIs are unstable and can
       * change at any time.
       */
      public static final class View {
        private List<String> config = Collections.emptyList();

        public List<String> getConfig() {
          return config;
        }

        public void setConfig(List<String> config) {
          this.config = config;
        }
      }

      private View view = new View();

      public View getView() {
        return view;
      }

      public void setView(View view) {
        this.view = view;
      }
    }

    /**
     * This class is internal and is hence not for public use. Its APIs are unstable and can change
     * at any time.
     */
    public static final class Resource {
      /**
       * This class is internal and is hence not for public use. Its APIs are unstable and can
       * change at any time.
       */
      public static final class Disabled {
        private List<String> keys = Collections.emptyList();

        public List<String> getKeys() {
          return keys;
        }

        public void setKeys(List<String> keys) {
          this.keys = keys;
        }
      }

      private Resource.Disabled disabled = new Resource.Disabled();

      public Resource.Disabled getDisabled() {
        return disabled;
      }

      public void setDisabled(Resource.Disabled disabled) {
        this.disabled = disabled;
      }
    }

    private Metrics metrics = new Metrics();
    private Resource resource = new Resource();

    public Metrics getMetrics() {
      return metrics;
    }

    public void setMetrics(Metrics metrics) {
      this.metrics = metrics;
    }

    public Resource getResource() {
      return resource;
    }

    public void setResource(Resource resource) {
      this.resource = resource;
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static final class HasExporters {
    private List<String> exporter = Collections.emptyList();

    public List<String> getExporter() {
      return exporter;
    }

    public void setExporter(List<String> exporter) {
      this.exporter = exporter;
    }
  }

  /**
   * This class is internal and is hence not for public use. Its APIs are unstable and can change at
   * any time.
   */
  public static final class Instrumentation {
    /**
     * This class is internal and is hence not for public use. Its APIs are unstable and can change
     * at any time.
     */
    public static final class Http {
      /**
       * This class is internal and is hence not for public use. Its APIs are unstable and can
       * change at any time.
       */
      public static final class Client {
        private List<String> captureRequestHeaders = Collections.emptyList();
        private List<String> captureResponseHeaders = Collections.emptyList();

        public List<String> getCaptureRequestHeaders() {
          return captureRequestHeaders;
        }

        public void setCaptureRequestHeaders(List<String> captureRequestHeaders) {
          this.captureRequestHeaders = captureRequestHeaders;
        }

        public List<String> getCaptureResponseHeaders() {
          return captureResponseHeaders;
        }

        public void setCaptureResponseHeaders(List<String> captureResponseHeaders) {
          this.captureResponseHeaders = captureResponseHeaders;
        }
      }

      /**
       * This class is internal and is hence not for public use. Its APIs are unstable and can
       * change at any time.
       */
      public static final class Server {
        private List<String> captureRequestHeaders = Collections.emptyList();
        private List<String> captureResponseHeaders = Collections.emptyList();

        public List<String> getCaptureRequestHeaders() {
          return captureRequestHeaders;
        }

        public void setCaptureRequestHeaders(List<String> captureRequestHeaders) {
          this.captureRequestHeaders = captureRequestHeaders;
        }

        public List<String> getCaptureResponseHeaders() {
          return captureResponseHeaders;
        }

        public void setCaptureResponseHeaders(List<String> captureResponseHeaders) {
          this.captureResponseHeaders = captureResponseHeaders;
        }
      }

      private Client client = new Client();

      private Server server = new Server();

      private List<String> knownMethods = Collections.emptyList();

      public Client getClient() {
        return client;
      }

      public void setClient(Client client) {
        this.client = client;
      }

      public Server getServer() {
        return server;
      }

      public void setServer(Server server) {
        this.server = server;
      }

      public List<String> getKnownMethods() {
        return knownMethods;
      }

      public void setKnownMethods(List<String> knownMethods) {
        this.knownMethods = knownMethods;
      }
    }

    private Http http = new Http();

    public Http getHttp() {
      return http;
    }

    public void setHttp(Http http) {
      this.http = http;
    }
  }

  private List<String> propagators = Collections.emptyList();

  private Java java = new Java();

  private Experimental experimental = new Experimental();

  private HasExporters logs = new HasExporters();

  private HasExporters metrics = new HasExporters();

  private HasExporters traces = new HasExporters();

  private Instrumentation instrumentation = new Instrumentation();

  public List<String> getPropagators() {
    return propagators;
  }

  public void setPropagators(List<String> propagators) {
    this.propagators = propagators;
  }

  public Java getJava() {
    return java;
  }

  public void setJava(Java java) {
    this.java = java;
  }

  public Experimental getExperimental() {
    return experimental;
  }

  public void setExperimental(Experimental experimental) {
    this.experimental = experimental;
  }

  public HasExporters getLogs() {
    return logs;
  }

  public void setLogs(HasExporters logs) {
    this.logs = logs;
  }

  public HasExporters getMetrics() {
    return metrics;
  }

  public void setMetrics(HasExporters metrics) {
    this.metrics = metrics;
  }

  public HasExporters getTraces() {
    return traces;
  }

  public void setTraces(HasExporters traces) {
    this.traces = traces;
  }

  public Instrumentation getInstrumentation() {
    return instrumentation;
  }

  public void setInstrumentation(Instrumentation instrumentation) {
    this.instrumentation = instrumentation;
  }

  public List<String> getJavaEnabledResourceProviders() {
    return java.getEnabled().getResource().getProviders();
  }

  public List<String> getJavaDisabledResourceProviders() {
    return java.getDisabled().getResource().getProviders();
  }

  public List<String> getExperimentalMetricsViewConfig() {
    return experimental.getMetrics().getView().getConfig();
  }

  public List<String> getExperimentalResourceDisabledKeys() {
    return experimental.getResource().getDisabled().getKeys();
  }

  public List<String> getLogsExporter() {
    return logs.getExporter();
  }

  public List<String> getMetricsExporter() {
    return metrics.getExporter();
  }

  public List<String> getTracesExporter() {
    return traces.getExporter();
  }

  public List<String> getHttpClientCaptureRequestHeaders() {
    return instrumentation.getHttp().getClient().getCaptureRequestHeaders();
  }

  public List<String> getHttpClientCaptureResponseHeaders() {
    return instrumentation.getHttp().getClient().getCaptureResponseHeaders();
  }

  public List<String> getHttpServerCaptureRequestHeaders() {
    return instrumentation.getHttp().getServer().getCaptureRequestHeaders();
  }

  public List<String> getHttpServerCaptureResponseHeaders() {
    return instrumentation.getHttp().getServer().getCaptureResponseHeaders();
  }

  public List<String> getHttpKnownMethods() {
    return instrumentation.getHttp().getKnownMethods();
  }
}
