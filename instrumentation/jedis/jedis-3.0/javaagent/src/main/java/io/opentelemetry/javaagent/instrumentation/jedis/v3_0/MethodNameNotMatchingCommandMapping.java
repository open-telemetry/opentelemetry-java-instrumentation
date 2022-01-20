package io.opentelemetry.javaagent.instrumentation.jedis.v3_0;

import static redis.clients.jedis.Protocol.Command.CLIENT;
import static redis.clients.jedis.Protocol.Command.CLUSTER;
import static redis.clients.jedis.Protocol.Command.CONFIG;
import static redis.clients.jedis.Protocol.Command.GEORADIUSBYMEMBER_RO;
import static redis.clients.jedis.Protocol.Command.GEORADIUS_RO;
import static redis.clients.jedis.Protocol.Command.MODULE;
import static redis.clients.jedis.Protocol.Command.OBJECT;
import static redis.clients.jedis.Protocol.Command.PUBSUB;
import static redis.clients.jedis.Protocol.Command.RESTORE;
import static redis.clients.jedis.Protocol.Command.SCRIPT;
import static redis.clients.jedis.Protocol.Command.SENTINEL;
import static redis.clients.jedis.Protocol.Command.SLOWLOG;
import static redis.clients.jedis.Protocol.Command.WAIT;
import static redis.clients.jedis.Protocol.Command.ZRANGE;
import static redis.clients.jedis.Protocol.Command.ZRANGEBYSCORE;
import static redis.clients.jedis.Protocol.Command.ZREVRANGE;
import static redis.clients.jedis.Protocol.Command.ZREVRANGEBYSCORE;

import io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver.ClusterSetSlotCommandResolver;
import io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver.CommandResolver;
import io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver.DefaultCommandResolver;
import io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver.KeywordCommandResolver;
import io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver.SentinelSetCommandResolver;
import io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver.SlaveofNoOneCommandResolver;
import io.opentelemetry.javaagent.instrumentation.jedis.v3_0.resolver.SubcommandResolver;
import redis.clients.jedis.Protocol;

public enum MethodNameNotMatchingCommandMapping {
  // in JedisCommands
  RESTOREREPLACE(new KeywordCommandResolver(RESTORE, Protocol.Keyword.REPLACE)),
  ZRANGEWITHSCORES(new KeywordCommandResolver(ZRANGE, Protocol.Keyword.WITHSCORES)),
  ZREVRANGEWITHSCORES(new KeywordCommandResolver(ZREVRANGE, Protocol.Keyword.WITHSCORES)),
  ZRANGEBYSCOREWITHSCORES(new KeywordCommandResolver(ZRANGEBYSCORE, Protocol.Keyword.WITHSCORES)),
  ZREVRANGEBYSCOREWITHSCORES(
      new KeywordCommandResolver(ZREVRANGEBYSCORE, Protocol.Keyword.WITHSCORES)),
  GEORADIUSREADONLY(new DefaultCommandResolver(GEORADIUS_RO)),
  GEORADIUSBYMEMBERREADONLY(new DefaultCommandResolver(GEORADIUSBYMEMBER_RO)),

  // in AdvancedJedisCommands
  CONFIGGET(new KeywordCommandResolver(CONFIG, Protocol.Keyword.GET)),
  CONFIGSET(new KeywordCommandResolver(CONFIG, Protocol.Keyword.SET)),
  SLOWLOGRESET(new KeywordCommandResolver(SLOWLOG, Protocol.Keyword.RESET)),
  SLOWLOGLEN(new KeywordCommandResolver(SLOWLOG, Protocol.Keyword.LEN)),
  SLOWLOGGET(new KeywordCommandResolver(SLOWLOG, Protocol.Keyword.GET)),
  OBJECTREFCOUNT(new KeywordCommandResolver(OBJECT, Protocol.Keyword.REFCOUNT)),
  OBJECTENCODING(new KeywordCommandResolver(OBJECT, Protocol.Keyword.ENCODING)),
  OBJECTIDLETIME(new KeywordCommandResolver(OBJECT, Protocol.Keyword.IDLETIME)),
  CLIENTKILL(new KeywordCommandResolver(CLIENT, Protocol.Keyword.KILL)),
  CLIENTGETNAME(new KeywordCommandResolver(CLIENT, Protocol.Keyword.GETNAME)),
  CLIENTLIST(new KeywordCommandResolver(CLIENT, Protocol.Keyword.LIST)),
  CLIENTSETNAME(new KeywordCommandResolver(CLIENT, Protocol.Keyword.SETNAME)),

  // in ScriptingCommands
  SCRIPTEXISTS(new KeywordCommandResolver(SCRIPT, Protocol.Keyword.EXISTS)),
  SCRIPTLOAD(new KeywordCommandResolver(SCRIPT, Protocol.Keyword.LOAD)),

  // in BasicCommands
  SLAVEOFNOONE(new SlaveofNoOneCommandResolver()),
  GETDB(null),
  CONFIGRESETSTAT(new KeywordCommandResolver(CONFIG, Protocol.Keyword.RESETSTAT)),
  CONFIGREWRITE(new KeywordCommandResolver(CONFIG, Protocol.Keyword.REWRITE)),
  WAITREPLICAS(new DefaultCommandResolver(WAIT)),

  // in ClusterCommands
  CLUSTERNODES(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_NODES)),
  CLUSTERMEET(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_MEET)),
  CLUSTERADDSLOTS(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_ADDSLOTS)),
  CLUSTERDELSLOTS(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_DELSLOTS)),
  CLUSTERINFO(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_INFO)),
  CLUSTERGETKEYSINSLOT(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_GETKEYSINSLOT)),
  CLUSTERSETSLOTNODE(new ClusterSetSlotCommandResolver(Protocol.CLUSTER_SETSLOT_NODE)),
  CLUSTERSETSLOTMIGRATING(new ClusterSetSlotCommandResolver(Protocol.CLUSTER_SETSLOT_MIGRATING)),
  CLUSTERSETSLOTIMPORTING(new ClusterSetSlotCommandResolver(Protocol.CLUSTER_SETSLOT_IMPORTING)),
  CLUSTERSETSLOTSTABLE(new ClusterSetSlotCommandResolver(Protocol.CLUSTER_SETSLOT_STABLE)),
  CLUSTERFORGET(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_FORGET)),
  CLUSTERFLUSHSLOTS(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_FLUSHSLOT)),
  CLUSTERKEYSLOT(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_KEYSLOT)),
  CLUSTERCOUNTKEYSINSLOT(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_COUNTKEYINSLOT)),
  CLUSTERSAVECONFIG(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_SAVECONFIG)),
  CLUSTERREPLICATE(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_REPLICATE)),
  CLUSTERSLAVES(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_SLAVES)),
  CLUSTERFAILOVER(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_FAILOVER)),
  CLUSTERSLOTS(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_SLOTS)),
  CLUSTERRESET(new SubcommandResolver(CLUSTER, Protocol.CLUSTER_RESET)),

  // in SentinelCommands
  SENTINELMASTERS(new SubcommandResolver(SENTINEL, Protocol.SENTINEL_MASTERS)),
  SENTINELGETMASTERADDRBYNAME(
      new SubcommandResolver(SENTINEL, Protocol.SENTINEL_GET_MASTER_ADDR_BY_NAME)),
  SENTINELRESET(new SubcommandResolver(SENTINEL, Protocol.SENTINEL_RESET)),
  SENTINELSLAVES(new SubcommandResolver(SENTINEL, Protocol.SENTINEL_SLAVES)),
  SENTINELFAILOVER(new SubcommandResolver(SENTINEL, Protocol.SENTINEL_FAILOVER)),
  SENTINELMONITOR(new SubcommandResolver(SENTINEL, Protocol.SENTINEL_MONITOR)),
  SENTINELREMOVE(new SubcommandResolver(SENTINEL, Protocol.SENTINEL_REMOVE)),
  SENTINELSET(new SentinelSetCommandResolver(SENTINEL)),

  // in ModuleCommands
  MODULELOAD(new KeywordCommandResolver(MODULE, Protocol.Keyword.LOAD)),
  MODULEUNLOAD(new KeywordCommandResolver(MODULE, Protocol.Keyword.UNLOAD)),
  MODULELIST(new KeywordCommandResolver(MODULE, Protocol.Keyword.LIST)),

  // in Jedis
  PUBSUBCHANNELS(new SubcommandResolver(PUBSUB, Protocol.PUBSUB_CHANNELS)),
  PUBSUBNUMPAT(new SubcommandResolver(PUBSUB, Protocol.PUBSUB_NUM_PAT)),
  PUBSUBNUMSUB(new SubcommandResolver(PUBSUB, Protocol.PUBSUB_NUMSUB)),
  ;

  public CommandResolver getResolver() {
    return resolver;
  }

  private final CommandResolver resolver;

  MethodNameNotMatchingCommandMapping(CommandResolver resolver) {
    this.resolver = resolver;
  }

  public static MethodNameNotMatchingCommandMapping mapping(String methodName) {
    for (MethodNameNotMatchingCommandMapping mapping : values()) {
      if (mapping.name().equalsIgnoreCase(methodName)) {
        return mapping;
      }
    }
    return null;
  }
}
