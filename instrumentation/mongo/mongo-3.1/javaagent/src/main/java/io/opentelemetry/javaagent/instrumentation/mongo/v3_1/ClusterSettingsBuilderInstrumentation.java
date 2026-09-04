/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.mongo.v3_1;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

import com.mongodb.ConnectionString;
import com.mongodb.ServerAddress;
import com.mongodb.connection.ClusterSettings;
import io.opentelemetry.instrumentation.mongo.v3_1.internal.MongoClusterSettings;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import java.util.List;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.matcher.ElementMatcher;

class ClusterSettingsBuilderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return named("com.mongodb.connection.ClusterSettings$Builder");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArguments(0)), getClass().getName() + "$InitializeAdvice");
    transformer.applyAdviceToMethod(
        isConstructor().and(takesArgument(0, named("com.mongodb.connection.ClusterSettings"))),
        getClass().getName() + "$CopyConstructorAdvice");
    transformer.applyAdviceToMethod(
        named("hosts").and(takesArgument(0, named("java.util.List"))),
        getClass().getName() + "$HostsAdvice");
    transformer.applyAdviceToMethod(
        named("applyConnectionString").and(takesArgument(0, named("com.mongodb.ConnectionString"))),
        getClass().getName() + "$ConnectionStringAdvice");
    transformer.applyAdviceToMethod(
        named("srvHost").and(takesArgument(0, named("java.lang.String"))),
        getClass().getName() + "$SrvHostAdvice");
    transformer.applyAdviceToMethod(
        named("applySettings")
            .and(takesArgument(0, named("com.mongodb.connection.ClusterSettings"))),
        getClass().getName() + "$ApplySettingsAdvice");
    transformer.applyAdviceToMethod(
        named("build").and(takesArguments(0)), getClass().getName() + "$BuildAdvice");
  }

  @SuppressWarnings("unused")
  public static class InitializeAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void initialize(@Advice.This ClusterSettings.Builder builder) {
      MongoClusterSettings.initialize(builder);
    }
  }

  @SuppressWarnings("unused")
  public static class CopyConstructorAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void copyConfiguration(
        @Advice.This ClusterSettings.Builder builder,
        @Advice.Argument(0) ClusterSettings settings) {
      MongoClusterSettings.applySettings(builder, settings);
    }
  }

  @SuppressWarnings("unused")
  public static class HostsAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureDirectHosts(
        @Advice.This ClusterSettings.Builder builder,
        @Advice.Argument(0) List<ServerAddress> hosts) {
      MongoClusterSettings.hosts(builder, hosts);
    }
  }

  @SuppressWarnings("unused")
  public static class ConnectionStringAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConnectionString(
        @Advice.This ClusterSettings.Builder builder,
        @Advice.Argument(0) ConnectionString connectionString) {
      MongoClusterSettings.connectionString(builder, connectionString);
    }
  }

  @SuppressWarnings("unused")
  public static class SrvHostAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureSrvHost(
        @Advice.This ClusterSettings.Builder builder, @Advice.Argument(0) String srvHost) {
      MongoClusterSettings.captureSrvHost(builder, srvHost);
    }
  }

  @SuppressWarnings("unused")
  public static class ApplySettingsAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void copyConfiguration(
        @Advice.This ClusterSettings.Builder builder,
        @Advice.Argument(0) ClusterSettings settings) {
      MongoClusterSettings.applySettings(builder, settings);
    }
  }

  @SuppressWarnings("unused")
  public static class BuildAdvice {

    @Advice.OnMethodExit(suppress = Throwable.class, inline = false)
    public static void captureConfiguration(
        @Advice.This ClusterSettings.Builder builder, @Advice.Return ClusterSettings settings) {
      MongoClusterSettings.built(builder, settings);
    }
  }
}
