/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.javaagent.instrumentation.jedis.v4_0;

import static net.bytebuddy.matcher.ElementMatchers.isDeclaredBy;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.namedOneOf;
import static net.bytebuddy.matcher.ElementMatchers.returns;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import io.opentelemetry.context.Scope;
import io.opentelemetry.instrumentation.api.incubator.semconv.db.internal.RedisServerTarget;
import io.opentelemetry.javaagent.extension.instrumentation.TypeInstrumentation;
import io.opentelemetry.javaagent.extension.instrumentation.TypeTransformer;
import io.opentelemetry.javaagent.extension.instrumentation.internal.AsmApi;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.asm.AsmVisitorWrapper;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.method.MethodList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.implementation.Implementation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.pool.TypePool;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import redis.clients.jedis.HostAndPort;
import redis.clients.jedis.JedisClusterInfoCache;

class JedisConnectionProviderInstrumentation implements TypeInstrumentation {

  @Override
  public ElementMatcher<TypeDescription> typeMatcher() {
    return namedOneOf(
        // 4.0.0-beta1
        "redis.clients.jedis.providers.JedisClusterConnectionProvider",
        // 4.0.0 and later
        "redis.clients.jedis.providers.ClusterConnectionProvider",
        "redis.clients.jedis.providers.ShardedConnectionProvider",
        // 4.4 and later
        "redis.clients.jedis.providers.SentineledConnectionProvider",
        "redis.clients.jedis.providers.SentineledConnectionProvider$SentinelListener",
        "redis.clients.jedis.JedisClusterInfoCache",
        "redis.clients.jedis.JedisClusterInfoCache$TopologyRefreshTask");
  }

  @Override
  public void transform(TypeTransformer transformer) {
    transformer.applyTransformer(
        (builder, typeDescription, classLoader, javaModule, protectionDomain) ->
            builder.visit(new TopologyRefreshTaskVisitor()));
    transformer.applyAdviceToMethod(
        named("initializeSlotsCache").and(takesArgument(0, named("java.util.Set"))),
        getClass().getName() + "$InitializeClusterAdvice");
    transformer.applyAdviceToMethod(
        named("initialize").and(takesArgument(0, named("java.util.List"))),
        getClass().getName() + "$InitializeShardsAdvice");
    transformer.applyAdviceToMethod(
        named("initSentinels").and(takesArgument(0, named("java.util.Set"))),
        getClass().getName() + "$InitializeSentinelsAdvice");
    transformer.applyAdviceToMethod(
        named("run")
            .and(
                isDeclaredBy(
                    named(
                        "redis.clients.jedis.providers.SentineledConnectionProvider$SentinelListener"))),
        getClass().getName() + "$SentinelListenerAdvice");
    transformer.applyAdviceToMethod(
        named("run")
            .and(
                isDeclaredBy(
                    named("redis.clients.jedis.JedisClusterInfoCache$TopologyRefreshTask"))),
        getClass().getName() + "$TopologyRefreshAdvice");
    transformer.applyAdviceToMethod(
        namedOneOf(
                "getConnection",
                "getConnectionFromSlot",
                "getReplicaConnection",
                "getReplicaConnectionFromSlot")
            .and(returns(named("redis.clients.jedis.Connection"))),
        getClass().getName() + "$ProviderTargetScopeAdvice");
    transformer.applyAdviceToMethod(
        named("initMaster").and(takesArgument(0, named("redis.clients.jedis.HostAndPort"))),
        getClass().getName() + "$ProviderTargetScopeAdvice");
    transformer.applyAdviceToMethod(
        named("renewSlotCache"), getClass().getName() + "$ProviderTargetScopeAdvice");
  }

  @SuppressWarnings("unused")
  public static class InitializeClusterAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(
        @Advice.This Object provider,
        @Advice.FieldValue("cache") @Nullable JedisClusterInfoCache cache,
        @Advice.Argument(0) @Nullable Set<HostAndPort> nodes) {
      RedisServerTarget target = JedisSingletons.targetOfNodes(nodes);
      JedisConfiguredTargets.setProviderTarget(provider, target);
      JedisConfiguredTargets.setTopologyTarget(cache, target);
      return JedisConfiguredTargets.openConfiguredTargetScope(target);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class InitializeShardsAdvice {

    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(
        @Advice.This Object provider, @Advice.Argument(0) @Nullable List<HostAndPort> shards) {
      RedisServerTarget target = JedisSingletons.targetOfShards(shards);
      JedisConfiguredTargets.setProviderTarget(provider, target);
      return JedisConfiguredTargets.openConfiguredTargetScope(target);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class InitializeSentinelsAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(
        @Advice.This Object provider,
        @Advice.FieldValue("masterName") @Nullable String masterName,
        @Advice.Argument(0) @Nullable Set<HostAndPort> sentinels) {
      RedisServerTarget target = JedisSingletons.targetOfSentinels(masterName, sentinels);
      JedisConfiguredTargets.setProviderTarget(provider, target);
      return JedisConfiguredTargets.openConfiguredTargetScope(target);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class SentinelListenerAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.FieldValue("this$0") Object provider) {
      return JedisConfiguredTargets.openProviderTargetScope(provider);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class TopologyRefreshAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.FieldValue("this$0") JedisClusterInfoCache cache) {
      return JedisConfiguredTargets.openTopologyTargetScope(cache);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  @SuppressWarnings("unused")
  public static class ProviderTargetScopeAdvice {

    @Nullable
    @Advice.OnMethodEnter(suppress = Throwable.class, inline = false)
    public static Scope onEnter(@Advice.This Object provider) {
      return JedisConfiguredTargets.openProviderTargetScope(provider);
    }

    @Advice.OnMethodExit(onThrowable = Throwable.class, suppress = Throwable.class, inline = false)
    public static void onExit(@Advice.Enter @Nullable Scope scope) {
      if (scope != null) {
        scope.close();
      }
    }
  }

  private static final class TopologyRefreshTaskVisitor implements AsmVisitorWrapper {

    private static final String CACHE_INTERNAL_NAME = "redis/clients/jedis/JedisClusterInfoCache";
    private static final String TASK_INTERNAL_NAME = CACHE_INTERNAL_NAME + "$TopologyRefreshTask";

    @Override
    public int mergeWriter(int flags) {
      return flags | ClassWriter.COMPUTE_MAXS;
    }

    @Override
    @CanIgnoreReturnValue
    public int mergeReader(int flags) {
      return flags;
    }

    @Override
    public ClassVisitor wrap(
        TypeDescription instrumentedType,
        ClassVisitor classVisitor,
        Implementation.Context implementationContext,
        TypePool typePool,
        FieldList<FieldDescription.InDefinedShape> fields,
        MethodList<?> methods,
        int writerFlags,
        int readerFlags) {
      if (!CACHE_INTERNAL_NAME.equals(instrumentedType.getInternalName())) {
        return classVisitor;
      }
      return new ClassVisitor(AsmApi.VERSION, classVisitor) {
        @Override
        public MethodVisitor visitMethod(
            int access, String name, String descriptor, String signature, String[] exceptions) {
          MethodVisitor methodVisitor =
              super.visitMethod(access, name, descriptor, signature, exceptions);
          if (!"<init>".equals(name)) {
            return methodVisitor;
          }
          return new MethodVisitor(api, methodVisitor) {
            @Override
            public void visitTypeInsn(int opcode, String type) {
              if (opcode == Opcodes.NEW && TASK_INTERNAL_NAME.equals(type)) {
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitFieldInsn(
                    Opcodes.GETFIELD, CACHE_INTERNAL_NAME, "startNodes", "Ljava/util/Set;");
                super.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    Type.getInternalName(JedisConfiguredTargets.class),
                    "setTopologyTargetFromNodes",
                    "(Lredis/clients/jedis/JedisClusterInfoCache;Ljava/util/Collection;)V",
                    false);
              }
              super.visitTypeInsn(opcode, type);
            }
          };
        }
      };
    }
  }
}
