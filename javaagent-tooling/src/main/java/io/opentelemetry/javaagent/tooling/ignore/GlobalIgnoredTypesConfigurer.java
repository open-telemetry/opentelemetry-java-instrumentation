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
import io.opentelemetry.javaagent.tooling.ExtensionClassLoader;

@AutoService(IgnoredTypesConfigurer.class)
public class GlobalIgnoredTypesConfigurer implements IgnoredTypesConfigurer {

  @Override
  public void configure(Config config, IgnoredTypesBuilder builder) {
    configureIgnoredTypes(builder);
    configureIgnoredClassLoaders(builder);
    configureIgnoredTasks(builder);
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

    // all classes in the AgentClassLoader are ignored separately
    // this is used to ignore agent classes that are in the bootstrap class loader
    // the reason not to use "io.opentelemetry.javaagent." is so that javaagent instrumentation
    // tests under "io.opentelemetry.javaagent." will still be instrumented
    builder.ignoreClass("io.opentelemetry.javaagent.bootstrap.");
    builder.ignoreClass("io.opentelemetry.javaagent.instrumentation.api.");
    builder.ignoreClass("io.opentelemetry.javaagent.shaded.");
    builder.ignoreClass("io.opentelemetry.javaagent.slf4j.");

    builder
        .ignoreClass("java.")
        .allowClass("java.net.URL")
        .allowClass("java.net.HttpURLConnection")
        .allowClass("java.net.URLClassLoader")
        .allowClass("java.rmi.")
        .allowClass("java.util.concurrent.")
        .allowClass("java.lang.reflect.Proxy")
        .allowClass("java.lang.ClassLoader")
        // Ignore inner classes of ClassLoader to avoid
        // java.lang.ClassCircularityError: java/lang/ClassLoader$1
        // when SecurityManager is enabled. ClassLoader$1 is used in ClassLoader.checkPackageAccess
        .ignoreClass("java.lang.ClassLoader$")
        .allowClass("java.lang.invoke.InnerClassLambdaMetafactory")
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
        .ignoreClassLoader(ExtensionClassLoader.class.getName());

    builder
        .ignoreClassLoader("datadog.")
        .ignoreClassLoader("com.dynatrace.")
        .ignoreClassLoader("com.appdynamics.")
        .ignoreClassLoader("com.newrelic.agent.")
        .ignoreClassLoader("com.newrelic.api.agent.")
        .ignoreClassLoader("com.nr.agent.");
  }

  private static void configureIgnoredTasks(IgnoredTypesBuilder builder) {
    // ForkJoinPool threads are initialized lazily and continue to handle tasks similar to an
    // event loop. They should not have context propagated to the base of the thread, tasks
    // themselves will have it through other means.
    builder.ignoreTaskClass("java.util.concurrent.ForkJoinWorkerThread");

    // ThreadPoolExecutor worker threads may be initialized lazily and manage interruption of
    // other threads. The actual tasks being run on those threads will propagate context but
    // we should not propagate onto this management thread.
    builder.ignoreTaskClass("java.util.concurrent.ThreadPoolExecutor$Worker");

    // TODO Workaround for
    // https://github.com/open-telemetry/opentelemetry-java-instrumentation/issues/787
    builder.ignoreTaskClass("org.apache.tomcat.util.net.NioEndpoint$SocketProcessor");

    // HttpConnection implements Runnable. When async request is completed HttpConnection
    // may be sent to process next request while context from previous request hasn't been
    // cleared yet.
    builder.ignoreTaskClass("org.eclipse.jetty.server.HttpConnection");

    // Avoid context leak on jetty. Runnable submitted from SelectChannelEndPoint is used to
    // process a new request which should not have context from them current request.
    builder.ignoreTaskClass("org.eclipse.jetty.io.nio.SelectChannelEndPoint$");

    // Don't instrument the executor's own runnables. These runnables may never return until
    // netty shuts down.
    builder.ignoreTaskClass("io.netty.util.concurrent.SingleThreadEventExecutor$");
  }
}
