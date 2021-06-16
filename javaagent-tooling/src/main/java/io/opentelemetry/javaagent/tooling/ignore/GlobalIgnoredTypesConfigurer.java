/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.tooling.ignore;

import com.google.auto.service.AutoService;
import io.opentelemetry.instrumentation.api.config.Config;
import io.opentelemetry.javaagent.bootstrap.AgentClassLoader;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesBuilder;
import io.opentelemetry.javaagent.extension.ignore.IgnoredTypesConfigurer;
import io.opentelemetry.javaagent.tooling.ExporterClassLoader;
import io.opentelemetry.javaagent.tooling.ExtensionClassLoader;

@AutoService(IgnoredTypesConfigurer.class)
public class GlobalIgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(Config config, IgnoredTypesBuilder builder) {
    configureIgnoredTypes(builder);
    configureIgnoredClassLoaders(builder);
  }

  private static void configureIgnoredTypes(IgnoredTypesBuilder builder) {
    builder
        .ignoreClass("org.gradle.")
        .ignoreClass("net.bytebuddy.")
        .ignoreClass("jdk.")
        .ignoreClass("org.aspectj.")
        .ignoreClass("datadog.")
        .ignoreClass("com.intellij.rt.debugger.")
        .ignoreClass("com.p6spy.")
        .ignoreClass("com.dynatrace.")
        .ignoreClass("com.jloadtrace.")
        .ignoreClass("com.appdynamics.")
        .ignoreClass("com.newrelic.agent.")
        .ignoreClass("com.newrelic.api.agent.")
        .ignoreClass("com.nr.agent.")
        .ignoreClass("com.singularity.")
        .ignoreClass("com.jinspired.")
        .ignoreClass("org.jinspired.");

    // allow JDK HttpClient
    builder.allowClass("jdk.internal.net.http.");

    // groovy
    builder
        .ignoreClass("org.groovy.")
        .ignoreClass("org.apache.groovy.")
        .ignoreClass("org.codehaus.groovy.")
        // We seem to instrument some classes in runtime
        .allowClass("org.codehaus.groovy.runtime.");

    // clojure
    builder.ignoreClass("clojure.").ignoreClass("$fn__");

    builder
        .ignoreClass("io.opentelemetry.javaagent.")
        // FIXME: We should remove this once
        // https://github.com/raphw/byte-buddy/issues/558 is fixed
        .allowClass("io.opentelemetry.javaagent.instrumentation.api.concurrent.RunnableWrapper")
        .allowClass("io.opentelemetry.javaagent.instrumentation.api.concurrent.CallableWrapper");

    builder
        .ignoreClass("java.")
        .allowClass("java.net.URL")
        .allowClass("java.net.HttpURLConnection")
        .allowClass("java.net.URLClassLoader")
        .allowClass("java.rmi.")
        .allowClass("java.util.concurrent.")
        .allowClass("java.lang.reflect.Proxy")
        .allowClass("java.lang.ClassLoader")
        // Concurrent instrumentation modifies the structure of
        // Cleaner class incompatibly with java9+ modules.
        // Working around until a long-term fix for modules can be
        // put in place.
        .allowClass("java.util.logging.")
        .ignoreClass("java.util.logging.LogManager$Cleaner");

    builder
        .ignoreClass("com.sun.")
        .allowClass("com.sun.messaging.")
        .allowClass("com.sun.jersey.api.client")
        .allowClass("com.sun.appserv")
        .allowClass("com.sun.faces")
        .allowClass("com.sun.xml.ws");

    builder
        .ignoreClass("sun.")
        .allowClass("sun.net.www.protocol.")
        .allowClass("sun.rmi.server")
        .allowClass("sun.rmi.transport")
        .allowClass("sun.net.www.http.HttpClient");

    builder.ignoreClass("org.slf4j.").allowClass("org.slf4j.MDC");

    builder
        .ignoreClass("org.springframework.core.$Proxy")
        // Tapestry Proxy, check only specific class that we know would be instrumented since there
        // is no common prefix for its proxies other than "$". ByteBuddy fails to instrument this
        // proxy, and as there is no reason why it should be instrumented anyway, exclude it.
        .ignoreClass("$HttpServletRequest_");
  }

  private static void configureIgnoredClassLoaders(IgnoredTypesBuilder builder) {
    builder
        .ignoreClassLoader("org.codehaus.groovy.runtime.callsite.CallSiteClassLoader")
        .ignoreClassLoader("sun.reflect.DelegatingClassLoader")
        .ignoreClassLoader("jdk.internal.reflect.DelegatingClassLoader")
        .ignoreClassLoader("clojure.lang.DynamicClassLoader")
        .ignoreClassLoader("org.apache.cxf.common.util.ASMHelper$TypeHelperClassLoader")
        .ignoreClassLoader("sun.misc.Launcher$ExtClassLoader")
        .ignoreClassLoader(AgentClassLoader.class.getName())
        .ignoreClassLoader(ExporterClassLoader.class.getName())
        .ignoreClassLoader(ExtensionClassLoader.class.getName());

    builder
        .ignoreClassLoader("datadog.")
        .ignoreClassLoader("com.dynatrace.")
        .ignoreClassLoader("com.appdynamics.")
        .ignoreClassLoader("com.newrelic.agent.")
        .ignoreClassLoader("com.newrelic.api.agent.")
        .ignoreClassLoader("com.nr.agent.");
  }
}
