/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.matcher;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

/**
 * Additional global matchers that are used to reduce number of classes we try to apply expensive
 * matchers to.
 *
 * <p>This is separated from {@link GlobalIgnoresMatcher} to allow for better testing. The idea is
 * that we should be able to remove this matcher from the agent and all tests should still pass.
 * Moreover, no classes matched by this matcher should be modified during test run.
 *
 * <p>TODO: refactor/optimize this class (spring boot approach as a possible solution)
 */
public class AdditionalLibraryIgnoresMatcher<T extends TypeDescription>
    extends ElementMatcher.Junction.AbstractBase<T> {

  public static <T extends TypeDescription> Junction<T> additionalLibraryIgnoresMatcher() {
    return new AdditionalLibraryIgnoresMatcher<>();
  }

  /**
   * Be very careful about the types of matchers used in this section as they are called on every
   * class load, so they must be fast. Generally speaking try to only use name matchers as they
   * don't have to load additional info.
   */
  @Override
  public boolean matches(T target) {
    String name = target.getActualName();

    if (name.startsWith("com.beust.jcommander.")
        || name.startsWith("com.fasterxml.classmate.")
        || name.startsWith("com.github.mustachejava.")
        || name.startsWith("com.jayway.jsonpath.")
        || name.startsWith("com.lightbend.lagom.")
        || name.startsWith("javax.el.")
        || name.startsWith("org.apache.lucene.")
        || name.startsWith("org.apache.tartarus.")
        || name.startsWith("org.json.simple.")
        || name.startsWith("org.yaml.snakeyaml.")) {
      return true;
    }

    if (name.startsWith("net.sf.cglib.")) {
      return !name.equals("net.sf.cglib.core.internal.LoadingCache$2");
    }

    if (name.startsWith("org.springframework.")) {
      if (name.startsWith("org.springframework.aop.")
          || name.startsWith("org.springframework.cache.")
          || name.startsWith("org.springframework.dao.")
          || name.startsWith("org.springframework.ejb.")
          || name.startsWith("org.springframework.expression.")
          || name.startsWith("org.springframework.format.")
          || name.startsWith("org.springframework.jca.")
          || name.startsWith("org.springframework.jdbc.")
          || name.startsWith("org.springframework.jmx.")
          || name.startsWith("org.springframework.jndi.")
          || name.startsWith("org.springframework.lang.")
          || name.startsWith("org.springframework.messaging.")
          || name.startsWith("org.springframework.objenesis.")
          || name.startsWith("org.springframework.orm.")
          || name.startsWith("org.springframework.remoting.")
          || name.startsWith("org.springframework.scripting.")
          || name.startsWith("org.springframework.stereotype.")
          || name.startsWith("org.springframework.transaction.")
          || name.startsWith("org.springframework.ui.")
          || name.startsWith("org.springframework.validation.")) {
        return true;
      }

      if (name.startsWith("org.springframework.data.")) {
        if (name.equals("org.springframework.data.repository.core.support.RepositoryFactorySupport")
            || name.startsWith(
                "org.springframework.data.convert.ClassGeneratingEntityInstantiator$")
            || name.equals(
                "org.springframework.data.jpa.repository.config.InspectionClassLoader")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.amqp.")) {
        if (name.startsWith("org.springframework.amqp.rabbit.connection.")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.beans.")) {
        if (name.equals("org.springframework.beans.factory.support.DisposableBeanAdapter")
            || name.startsWith(
                "org.springframework.beans.factory.groovy.GroovyBeanDefinitionReader$")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.boot.")) {
        return !instrumentedSpringBootClasses(name)
            && !name.startsWith("org.springframework.boot.web.filter.")
            && !name.startsWith("org.springframework.boot.web.servlet.");
      }

      if (name.startsWith("org.springframework.cglib.")) {
        // This class contains nested Callable instance that we'd happily not touch, but
        // unfortunately our field injection code is not flexible enough to realize that, so instead
        // we instrument this Callable to make tests happy.
        if (name.startsWith("org.springframework.cglib.core.internal.LoadingCache$")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.context.")) {
        // More runnables to deal with
        if (name.startsWith("org.springframework.context.support.AbstractApplicationContext$")
            || name.equals("org.springframework.context.support.ContextTypeMatchClassLoader")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.core.")) {
        if (name.startsWith("org.springframework.core.task.")
            || name.equals("org.springframework.core.DecoratingClassLoader")
            || name.equals("org.springframework.core.OverridingClassLoader")
            || name.equals(
                "org.springframework.core.ReactiveAdapterRegistry$EmptyCompletableFuture")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.instrument.")) {
        if (name.equals("org.springframework.instrument.classloading.SimpleThrowawayClassLoader")
            || name.equals("org.springframework.instrument.classloading.ShadowingClassLoader")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.http.")) {
        // There are some Mono implementation that get instrumented
        if (name.startsWith("org.springframework.http.server.reactive.")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.jms.")) {
        if (name.startsWith("org.springframework.jms.listener.")
            || name.equals(
                "org.springframework.jms.config.JmsListenerEndpointRegistry$AggregatingCallback")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.util.")) {
        if (name.startsWith("org.springframework.util.concurrent.")) {
          return false;
        }
        return true;
      }

      if (name.startsWith("org.springframework.web.")) {
        if (name.startsWith("org.springframework.web.servlet.")
            || name.startsWith("org.springframework.web.filter.")
            || name.startsWith("org.springframework.web.reactive.")
            || name.startsWith("org.springframework.web.context.request.async.")
            || name.equals(
                "org.springframework.web.context.support.AbstractRefreshableWebApplicationContext")
            || name.equals("org.springframework.web.context.support.GenericWebApplicationContext")
            || name.equals("org.springframework.web.context.support.XmlWebApplicationContext")) {
          return false;
        }
        return true;
      }

      return false;
    }

    // xml-apis, xerces, xalan, but not xml web-services
    if ((name.startsWith("javax.xml.") && !name.startsWith("javax.xml.ws."))
        || name.startsWith("org.apache.bcel.")
        || name.startsWith("org.apache.html.")
        || name.startsWith("org.apache.regexp.")
        || name.startsWith("org.apache.wml.")
        || name.startsWith("org.apache.xalan.")
        || name.startsWith("org.apache.xerces.")
        || name.startsWith("org.apache.xml.")
        || name.startsWith("org.apache.xpath.")
        || name.startsWith("org.xml.")) {
      return true;
    }

    if (name.startsWith("ch.qos.logback.")) {
      // We instrument this Runnable
      if (name.equals("ch.qos.logback.core.AsyncAppenderBase$Worker")) {
        return false;
      }
      // Allow instrumenting loggers & events
      if (name.equals("ch.qos.logback.classic.Logger")
          || name.equals("ch.qos.logback.classic.spi.LoggingEvent")
          || name.equals("ch.qos.logback.classic.spi.LoggingEventVO")) {
        return false;
      }
      return true;
    }

    if (name.startsWith("com.codahale.metrics.")) {
      // We instrument servlets
      if (name.startsWith("com.codahale.metrics.servlets.")) {
        return false;
      }
      return true;
    }

    if (name.startsWith("com.couchbase.client.deps.")) {
      // Couchbase library includes some packaged dependencies, unfortunately some of them are
      // instrumented by executors instrumentation
      if (name.startsWith("com.couchbase.client.deps.io.netty.")
          || name.startsWith("com.couchbase.client.deps.org.LatencyUtils.")
          || name.startsWith("com.couchbase.client.deps.com.lmax.disruptor.")) {
        return false;
      }
      return true;
    }

    if (name.startsWith("com.google.cloud.")
        || name.startsWith("com.google.instrumentation.")
        || name.startsWith("com.google.j2objc.")
        || name.startsWith("com.google.gson.")
        || name.startsWith("com.google.logging.")
        || name.startsWith("com.google.longrunning.")
        || name.startsWith("com.google.protobuf.")
        || name.startsWith("com.google.rpc.")
        || name.startsWith("com.google.thirdparty.")
        || name.startsWith("com.google.type.")) {
      return true;
    }
    if (name.startsWith("com.google.common.")) {
      if (name.startsWith("com.google.common.util.concurrent.")
          || name.equals("com.google.common.base.internal.Finalizer")) {
        return false;
      }
      return true;
    }
    if (name.startsWith("com.google.inject.")) {
      // We instrument Runnable there
      if (name.startsWith("com.google.inject.internal.AbstractBindingProcessor$")
          || name.startsWith("com.google.inject.internal.BytecodeGen$")
          || name.startsWith("com.google.inject.internal.cglib.core.internal.$LoadingCache$")) {
        return false;
      }
      // We instrument Callable there
      if (name.startsWith("com.google.inject.internal.cglib.core.internal.$LoadingCache$")) {
        return false;
      }
      return true;
    }
    if (name.startsWith("com.google.api.")) {
      if (name.startsWith("com.google.api.client.http.HttpRequest")) {
        return false;
      }
      return true;
    }

    if (name.startsWith("org.h2.")) {
      if (name.equals("org.h2.Driver")
          || name.startsWith("org.h2.jdbc.")
          || name.startsWith("org.h2.jdbcx.")
          // Some runnables that get instrumented
          || name.equals("org.h2.util.Task")
          || name.equals("org.h2.store.FileLock")
          || name.equals("org.h2.engine.DatabaseCloser")
          || name.equals("org.h2.engine.OnExitDatabaseCloser")) {
        return false;
      }
      return true;
    }

    if (name.startsWith("com.carrotsearch.hppc.")) {
      if (name.startsWith("com.carrotsearch.hppc.HashOrderMixing$")) {
        return false;
      }
      return true;
    }

    if (name.startsWith("com.fasterxml.jackson.")) {
      if (name.equals("com.fasterxml.jackson.module.afterburner.util.MyClassLoader")) {
        return false;
      }
      return true;
    }

    // kotlin, note we do not ignore kotlinx because we instrument coroutins code
    if (name.startsWith("kotlin.")) {
      if (name.equals("kotlin.coroutines.jvm.internal.DebugProbesKt")) {
        return false;
      }
      return true;
    }

    return false;
  }

  private static final Set<String> INSTRUMENTED_SPRING_BOOT_CLASSES;

  static {
    Set<String> instrumented = new HashSet<>();
    instrumented.add("org.springframework.boot.autoconfigure.BackgroundPreinitializer$");
    instrumented.add("org.springframework.boot.autoconfigure.condition.OnClassCondition$");
    instrumented.add("org.springframework.boot.web.embedded.netty.NettyWebServer$");
    instrumented.add(
        "org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainer$");
    instrumented.add(
        "org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedWebappClassLoader");
    instrumented.add("org.springframework.boot.context.embedded.EmbeddedWebApplicationContext");
    instrumented.add(
        "org.springframework.boot.context.embedded.AnnotationConfigEmbeddedWebApplicationContext");
    // spring boot 2 classes
    instrumented.add(
        "org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext");
    instrumented.add(
        "org.springframework.boot.web.servlet.context.AnnotationConfigServletWebServerApplicationContext");
    instrumented.add("org.springframework.boot.web.embedded.tomcat.TomcatWebServer$");
    instrumented.add(
        "org.springframework.boot.web.embedded.tomcat.TomcatEmbeddedWebappClassLoader");
    instrumented.add("org.springframework.boot.web.servlet.DelegatingFilterProxyRegistrationBean$");
    INSTRUMENTED_SPRING_BOOT_CLASSES = Collections.unmodifiableSet(instrumented);
  }

  private static String outerClassName(final String name) {
    int separator = name.indexOf('$');
    return (separator == -1 ? name : name.substring(0, separator + 1));
  }

  private static boolean instrumentedSpringBootClasses(final String name) {
    String outerName = outerClassName(name);
    return INSTRUMENTED_SPRING_BOOT_CLASSES.contains(outerName);
  }

  @Override
  public String toString() {
    return "additionalLibraryIgnoresMatcher()";
  }

  @Override
  public boolean equals(Object obj) {
    // all instances are the same
    return obj instanceof AdditionalLibraryIgnoresMatcher;
  }

  @Override
  public int hashCode() {
    return 17;
  }
}
