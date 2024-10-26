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

  private List<String> propagators = Collections.emptyList();

  private Java java = new Java();

  private Experimental experimental = new Experimental();

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
}
