/*
 * Copyright The OpenTelemetry Authors
 * SPDX-License-Identifier: Apache-2.0
 */

package io.opentelemetry.semconv;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.semconv.incubating.CodeIncubatingAttributes;
import io.opentelemetry.semconv.incubating.DbIncubatingAttributes;
import io.opentelemetry.semconv.incubating.EnduserIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ExceptionIncubatingAttributes;
import io.opentelemetry.semconv.incubating.HttpIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessageIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.opentelemetry.semconv.incubating.PeerIncubatingAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import java.util.List;

public class SemanticAttributes {

  private SemanticAttributes() {}

  public static final AttributeKey<String> URL_FULL = UrlAttributes.URL_FULL;
  public static final AttributeKey<String> URL_PATH = UrlAttributes.URL_PATH;
  public static final AttributeKey<String> URL_QUERY = UrlAttributes.URL_QUERY;
  public static final AttributeKey<String> URL_SCHEME = UrlAttributes.URL_SCHEME;

  public static final AttributeKey<String> NETWORK_LOCAL_ADDRESS =
      NetworkAttributes.NETWORK_LOCAL_ADDRESS;
  public static final AttributeKey<Long> NETWORK_LOCAL_PORT = NetworkAttributes.NETWORK_LOCAL_PORT;
  public static final AttributeKey<String> NETWORK_PEER_ADDRESS =
      NetworkAttributes.NETWORK_PEER_ADDRESS;
  public static final AttributeKey<Long> NETWORK_PEER_PORT = NetworkAttributes.NETWORK_PEER_PORT;
  public static final AttributeKey<String> NETWORK_PROTOCOL_NAME =
      NetworkAttributes.NETWORK_PROTOCOL_NAME;
  public static final AttributeKey<String> NETWORK_PROTOCOL_VERSION =
      NetworkAttributes.NETWORK_PROTOCOL_VERSION;
  public static final AttributeKey<String> NETWORK_TRANSPORT = NetworkAttributes.NETWORK_TRANSPORT;
  public static final AttributeKey<String> NETWORK_TYPE = NetworkAttributes.NETWORK_TYPE;

  public static final AttributeKeyTemplate<List<String>> HTTP_REQUEST_HEADER =
      HttpAttributes.HTTP_REQUEST_HEADER;
  public static final AttributeKey<String> HTTP_REQUEST_METHOD = HttpAttributes.HTTP_REQUEST_METHOD;
  public static final AttributeKey<String> HTTP_REQUEST_METHOD_ORIGINAL =
      HttpAttributes.HTTP_REQUEST_METHOD_ORIGINAL;
  public static final AttributeKey<Long> HTTP_REQUEST_RESEND_COUNT =
      HttpAttributes.HTTP_REQUEST_RESEND_COUNT;
  public static final AttributeKeyTemplate<List<String>> HTTP_RESPONSE_HEADER =
      HttpAttributes.HTTP_RESPONSE_HEADER;
  public static final AttributeKey<Long> HTTP_RESPONSE_STATUS_CODE =
      HttpAttributes.HTTP_RESPONSE_STATUS_CODE;
  public static final AttributeKey<String> HTTP_ROUTE = HttpAttributes.HTTP_ROUTE;

  public static final AttributeKey<Long> HTTP_REQUEST_BODY_SIZE =
      HttpIncubatingAttributes.HTTP_REQUEST_BODY_SIZE;
  public static final AttributeKey<Long> HTTP_RESPONSE_BODY_SIZE =
      HttpIncubatingAttributes.HTTP_RESPONSE_BODY_SIZE;

  public static final AttributeKey<String> ERROR_TYPE = ErrorAttributes.ERROR_TYPE;

  public static final AttributeKey<String> USER_AGENT_ORIGINAL =
      UserAgentAttributes.USER_AGENT_ORIGINAL;

  public static final AttributeKey<String> CLIENT_ADDRESS = ClientAttributes.CLIENT_ADDRESS;
  public static final AttributeKey<Long> CLIENT_PORT = ClientAttributes.CLIENT_PORT;

  public static final AttributeKey<String> SERVER_ADDRESS = ServerAttributes.SERVER_ADDRESS;
  public static final AttributeKey<Long> SERVER_PORT = ServerAttributes.SERVER_PORT;

  public static final AttributeKey<String> DB_CASSANDRA_CONSISTENCY_LEVEL =
      DbIncubatingAttributes.DB_CASSANDRA_CONSISTENCY_LEVEL;
  public static final AttributeKey<String> DB_CASSANDRA_COORDINATOR_DC =
      DbIncubatingAttributes.DB_CASSANDRA_COORDINATOR_DC;
  public static final AttributeKey<String> DB_CASSANDRA_COORDINATOR_ID =
      DbIncubatingAttributes.DB_CASSANDRA_COORDINATOR_ID;
  public static final AttributeKey<Boolean> DB_CASSANDRA_IDEMPOTENCE =
      DbIncubatingAttributes.DB_CASSANDRA_IDEMPOTENCE;
  public static final AttributeKey<Long> DB_CASSANDRA_PAGE_SIZE =
      DbIncubatingAttributes.DB_CASSANDRA_PAGE_SIZE;
  public static final AttributeKey<Long> DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT =
      DbIncubatingAttributes.DB_CASSANDRA_SPECULATIVE_EXECUTION_COUNT;
  public static final AttributeKey<String> DB_CASSANDRA_TABLE =
      DbIncubatingAttributes.DB_CASSANDRA_TABLE;
  public static final AttributeKey<String> DB_CONNECTION_STRING =
      DbIncubatingAttributes.DB_CONNECTION_STRING;
  public static final AttributeKey<String> DB_ELASTICSEARCH_CLUSTER_NAME =
      DbIncubatingAttributes.DB_ELASTICSEARCH_CLUSTER_NAME;
  public static final AttributeKey<String> DB_ELASTICSEARCH_NODE_NAME =
      DbIncubatingAttributes.DB_ELASTICSEARCH_NODE_NAME;
  public static final AttributeKeyTemplate<String> DB_ELASTICSEARCH_PATH_PARTS =
      DbIncubatingAttributes.DB_ELASTICSEARCH_PATH_PARTS;
  public static final AttributeKey<String> DB_INSTANCE_ID = DbIncubatingAttributes.DB_INSTANCE_ID;
  public static final AttributeKey<String> DB_JDBC_DRIVER_CLASSNAME =
      DbIncubatingAttributes.DB_JDBC_DRIVER_CLASSNAME;
  public static final AttributeKey<String> DB_MSSQL_INSTANCE_NAME =
      DbIncubatingAttributes.DB_MSSQL_INSTANCE_NAME;
  public static final AttributeKey<String> DB_NAME = DbIncubatingAttributes.DB_NAME;
  public static final AttributeKey<String> DB_OPERATION = DbIncubatingAttributes.DB_OPERATION;
  public static final AttributeKey<Long> DB_REDIS_DATABASE_INDEX =
      DbIncubatingAttributes.DB_REDIS_DATABASE_INDEX;
  public static final AttributeKey<String> DB_SQL_TABLE = DbIncubatingAttributes.DB_SQL_TABLE;
  public static final AttributeKey<String> DB_STATEMENT = DbIncubatingAttributes.DB_STATEMENT;
  public static final AttributeKey<String> DB_SYSTEM = DbIncubatingAttributes.DB_SYSTEM;
  public static final AttributeKey<String> DB_USER = DbIncubatingAttributes.DB_USER;

  public static final AttributeKey<String> DB_MONGODB_COLLECTION =
      DbIncubatingAttributes.DB_MONGODB_COLLECTION;

  public static final class DbSystemValues {
    public static final String OTHER_SQL = DbIncubatingAttributes.DbSystemValues.OTHER_SQL;
    public static final String MSSQL = DbIncubatingAttributes.DbSystemValues.MSSQL;
    public static final String MSSQLCOMPACT = DbIncubatingAttributes.DbSystemValues.MSSQLCOMPACT;
    public static final String MYSQL = DbIncubatingAttributes.DbSystemValues.MYSQL;
    public static final String ORACLE = DbIncubatingAttributes.DbSystemValues.ORACLE;
    public static final String DB2 = DbIncubatingAttributes.DbSystemValues.DB2;
    public static final String POSTGRESQL = DbIncubatingAttributes.DbSystemValues.POSTGRESQL;
    public static final String REDSHIFT = DbIncubatingAttributes.DbSystemValues.REDSHIFT;
    public static final String HIVE = DbIncubatingAttributes.DbSystemValues.HIVE;
    public static final String CLOUDSCAPE = DbIncubatingAttributes.DbSystemValues.CLOUDSCAPE;
    public static final String HSQLDB = DbIncubatingAttributes.DbSystemValues.HSQLDB;
    public static final String PROGRESS = DbIncubatingAttributes.DbSystemValues.PROGRESS;
    public static final String MAXDB = DbIncubatingAttributes.DbSystemValues.MAXDB;
    public static final String HANADB = DbIncubatingAttributes.DbSystemValues.HANADB;
    public static final String INGRES = DbIncubatingAttributes.DbSystemValues.INGRES;
    public static final String FIRSTSQL = DbIncubatingAttributes.DbSystemValues.FIRSTSQL;
    public static final String EDB = DbIncubatingAttributes.DbSystemValues.EDB;
    public static final String CACHE = DbIncubatingAttributes.DbSystemValues.CACHE;
    public static final String ADABAS = DbIncubatingAttributes.DbSystemValues.ADABAS;
    public static final String FIREBIRD = DbIncubatingAttributes.DbSystemValues.FIREBIRD;
    public static final String DERBY = DbIncubatingAttributes.DbSystemValues.DERBY;
    public static final String FILEMAKER = DbIncubatingAttributes.DbSystemValues.FILEMAKER;
    public static final String INFORMIX = DbIncubatingAttributes.DbSystemValues.INFORMIX;
    public static final String INSTANTDB = DbIncubatingAttributes.DbSystemValues.INSTANTDB;
    public static final String INTERBASE = DbIncubatingAttributes.DbSystemValues.INTERBASE;
    public static final String MARIADB = DbIncubatingAttributes.DbSystemValues.MARIADB;
    public static final String NETEZZA = DbIncubatingAttributes.DbSystemValues.NETEZZA;
    public static final String PERVASIVE = DbIncubatingAttributes.DbSystemValues.PERVASIVE;
    public static final String POINTBASE = DbIncubatingAttributes.DbSystemValues.POINTBASE;
    public static final String SQLITE = DbIncubatingAttributes.DbSystemValues.SQLITE;
    public static final String SYBASE = DbIncubatingAttributes.DbSystemValues.SYBASE;
    public static final String TERADATA = DbIncubatingAttributes.DbSystemValues.TERADATA;
    public static final String VERTICA = DbIncubatingAttributes.DbSystemValues.VERTICA;
    public static final String H2 = DbIncubatingAttributes.DbSystemValues.H2;
    public static final String COLDFUSION = DbIncubatingAttributes.DbSystemValues.COLDFUSION;
    public static final String CASSANDRA = DbIncubatingAttributes.DbSystemValues.CASSANDRA;
    public static final String HBASE = DbIncubatingAttributes.DbSystemValues.HBASE;
    public static final String MONGODB = DbIncubatingAttributes.DbSystemValues.MONGODB;
    public static final String REDIS = DbIncubatingAttributes.DbSystemValues.REDIS;
    public static final String COUCHBASE = DbIncubatingAttributes.DbSystemValues.COUCHBASE;
    public static final String COUCHDB = DbIncubatingAttributes.DbSystemValues.COUCHDB;
    public static final String COSMOSDB = DbIncubatingAttributes.DbSystemValues.COSMOSDB;
    public static final String DYNAMODB = DbIncubatingAttributes.DbSystemValues.DYNAMODB;
    public static final String NEO4J = DbIncubatingAttributes.DbSystemValues.NEO4J;
    public static final String GEODE = DbIncubatingAttributes.DbSystemValues.GEODE;
    public static final String ELASTICSEARCH = DbIncubatingAttributes.DbSystemValues.ELASTICSEARCH;
    public static final String MEMCACHED = DbIncubatingAttributes.DbSystemValues.MEMCACHED;
    public static final String COCKROACHDB = DbIncubatingAttributes.DbSystemValues.COCKROACHDB;
    public static final String OPENSEARCH = DbIncubatingAttributes.DbSystemValues.OPENSEARCH;
    public static final String CLICKHOUSE = DbIncubatingAttributes.DbSystemValues.CLICKHOUSE;
    public static final String SPANNER = DbIncubatingAttributes.DbSystemValues.SPANNER;
    public static final String TRINO = DbIncubatingAttributes.DbSystemValues.TRINO;

    private DbSystemValues() {}
  }

  public static final AttributeKey<String> RPC_CONNECT_RPC_ERROR_CODE =
      RpcIncubatingAttributes.RPC_CONNECT_RPC_ERROR_CODE;
  public static final AttributeKeyTemplate<List<String>> RPC_CONNECT_RPC_REQUEST_METADATA =
      RpcIncubatingAttributes.RPC_CONNECT_RPC_REQUEST_METADATA;
  public static final AttributeKeyTemplate<List<String>> RPC_CONNECT_RPC_RESPONSE_METADATA =
      RpcIncubatingAttributes.RPC_CONNECT_RPC_RESPONSE_METADATA;
  public static final AttributeKeyTemplate<List<String>> RPC_GRPC_REQUEST_METADATA =
      RpcIncubatingAttributes.RPC_GRPC_REQUEST_METADATA;
  public static final AttributeKeyTemplate<List<String>> RPC_GRPC_RESPONSE_METADATA =
      RpcIncubatingAttributes.RPC_GRPC_RESPONSE_METADATA;
  public static final AttributeKey<Long> RPC_GRPC_STATUS_CODE =
      RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE;
  public static final AttributeKey<Long> RPC_JSONRPC_ERROR_CODE =
      RpcIncubatingAttributes.RPC_JSONRPC_ERROR_CODE;
  public static final AttributeKey<String> RPC_JSONRPC_ERROR_MESSAGE =
      RpcIncubatingAttributes.RPC_JSONRPC_ERROR_MESSAGE;
  public static final AttributeKey<String> RPC_JSONRPC_REQUEST_ID =
      RpcIncubatingAttributes.RPC_JSONRPC_REQUEST_ID;
  public static final AttributeKey<String> RPC_JSONRPC_VERSION =
      RpcIncubatingAttributes.RPC_JSONRPC_VERSION;
  public static final AttributeKey<String> RPC_METHOD = RpcIncubatingAttributes.RPC_METHOD;
  public static final AttributeKey<String> RPC_SERVICE = RpcIncubatingAttributes.RPC_SERVICE;
  public static final AttributeKey<String> RPC_SYSTEM = RpcIncubatingAttributes.RPC_SYSTEM;

  public static final class RpcSystemValues {
    public static final String GRPC = RpcIncubatingAttributes.RpcSystemValues.GRPC;
    public static final String JAVA_RMI = RpcIncubatingAttributes.RpcSystemValues.JAVA_RMI;
    public static final String DOTNET_WCF = RpcIncubatingAttributes.RpcSystemValues.DOTNET_WCF;
    public static final String APACHE_DUBBO = RpcIncubatingAttributes.RpcSystemValues.APACHE_DUBBO;
    public static final String CONNECT_RPC = RpcIncubatingAttributes.RpcSystemValues.CONNECT_RPC;

    private RpcSystemValues() {}
  }

  public static final class RpcGrpcStatusCodeValues {
    public static final long OK = RpcIncubatingAttributes.RpcGrpcStatusCodeValues.OK;
    public static final long CANCELLED = RpcIncubatingAttributes.RpcGrpcStatusCodeValues.CANCELLED;
    public static final long UNKNOWN = RpcIncubatingAttributes.RpcGrpcStatusCodeValues.UNKNOWN;
    public static final long INVALID_ARGUMENT =
        RpcIncubatingAttributes.RpcGrpcStatusCodeValues.INVALID_ARGUMENT;
    public static final long DEADLINE_EXCEEDED =
        RpcIncubatingAttributes.RpcGrpcStatusCodeValues.DEADLINE_EXCEEDED;
    public static final long NOT_FOUND = RpcIncubatingAttributes.RpcGrpcStatusCodeValues.NOT_FOUND;
    public static final long ALREADY_EXISTS =
        RpcIncubatingAttributes.RpcGrpcStatusCodeValues.ALREADY_EXISTS;
    public static final long PERMISSION_DENIED =
        RpcIncubatingAttributes.RpcGrpcStatusCodeValues.PERMISSION_DENIED;
    public static final long RESOURCE_EXHAUSTED =
        RpcIncubatingAttributes.RpcGrpcStatusCodeValues.RESOURCE_EXHAUSTED;
    public static final long FAILED_PRECONDITION =
        RpcIncubatingAttributes.RpcGrpcStatusCodeValues.FAILED_PRECONDITION;
    public static final long ABORTED = RpcIncubatingAttributes.RpcGrpcStatusCodeValues.ABORTED;
    public static final long OUT_OF_RANGE =
        RpcIncubatingAttributes.RpcGrpcStatusCodeValues.OUT_OF_RANGE;
    public static final long UNIMPLEMENTED =
        RpcIncubatingAttributes.RpcGrpcStatusCodeValues.UNIMPLEMENTED;
    public static final long INTERNAL = RpcIncubatingAttributes.RpcGrpcStatusCodeValues.INTERNAL;
    public static final long UNAVAILABLE =
        RpcIncubatingAttributes.RpcGrpcStatusCodeValues.UNAVAILABLE;
    public static final long DATA_LOSS = RpcIncubatingAttributes.RpcGrpcStatusCodeValues.DATA_LOSS;
    public static final long UNAUTHENTICATED =
        RpcIncubatingAttributes.RpcGrpcStatusCodeValues.UNAUTHENTICATED;

    private RpcGrpcStatusCodeValues() {}
  }

  public static final class RpcConnectRpcErrorCodeValues {
    public static final String CANCELLED =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.CANCELLED;
    public static final String UNKNOWN =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.UNKNOWN;
    public static final String INVALID_ARGUMENT =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.INVALID_ARGUMENT;
    public static final String DEADLINE_EXCEEDED =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.DEADLINE_EXCEEDED;
    public static final String NOT_FOUND =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.NOT_FOUND;
    public static final String ALREADY_EXISTS =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.ALREADY_EXISTS;
    public static final String PERMISSION_DENIED =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.PERMISSION_DENIED;
    public static final String RESOURCE_EXHAUSTED =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.RESOURCE_EXHAUSTED;
    public static final String FAILED_PRECONDITION =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.FAILED_PRECONDITION;
    public static final String ABORTED =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.ABORTED;
    public static final String OUT_OF_RANGE =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.OUT_OF_RANGE;
    public static final String UNIMPLEMENTED =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.UNIMPLEMENTED;
    public static final String INTERNAL =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.INTERNAL;
    public static final String UNAVAILABLE =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.UNAVAILABLE;
    public static final String DATA_LOSS =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.DATA_LOSS;
    public static final String UNAUTHENTICATED =
        RpcIncubatingAttributes.RpcConnectRpcErrorCodeValues.UNAUTHENTICATED;

    private RpcConnectRpcErrorCodeValues() {}
  }

  public static final AttributeKey<Long> MESSAGING_BATCH_MESSAGE_COUNT =
      MessagingIncubatingAttributes.MESSAGING_BATCH_MESSAGE_COUNT;
  public static final AttributeKey<String> MESSAGING_CLIENT_ID =
      MessagingIncubatingAttributes.MESSAGING_CLIENT_ID;
  public static final AttributeKey<Boolean> MESSAGING_DESTINATION_ANONYMOUS =
      MessagingIncubatingAttributes.MESSAGING_DESTINATION_ANONYMOUS;

  @Deprecated
  public static final AttributeKey<String> MESSAGING_DESTINATION =
      AttributeKey.stringKey("messaging.destination");

  public static final AttributeKey<String> MESSAGING_DESTINATION_NAME =
      MessagingIncubatingAttributes.MESSAGING_DESTINATION_NAME;
  public static final AttributeKey<String> MESSAGING_DESTINATION_TEMPLATE =
      MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPLATE;
  public static final AttributeKey<Boolean> MESSAGING_DESTINATION_TEMPORARY =
      MessagingIncubatingAttributes.MESSAGING_DESTINATION_TEMPORARY;
  public static final AttributeKey<Boolean> MESSAGING_DESTINATION_PUBLISH_ANONYMOUS =
      MessagingIncubatingAttributes.MESSAGING_DESTINATION_PUBLISH_ANONYMOUS;
  public static final AttributeKey<String> MESSAGING_DESTINATION_PUBLISH_NAME =
      MessagingIncubatingAttributes.MESSAGING_DESTINATION_PUBLISH_NAME;
  public static final AttributeKey<String> MESSAGING_GCP_PUBSUB_MESSAGE_ORDERING_KEY =
      MessagingIncubatingAttributes.MESSAGING_GCP_PUBSUB_MESSAGE_ORDERING_KEY;
  public static final AttributeKey<String> MESSAGING_KAFKA_CONSUMER_GROUP =
      MessagingIncubatingAttributes.MESSAGING_KAFKA_CONSUMER_GROUP;
  public static final AttributeKey<Long> MESSAGING_KAFKA_DESTINATION_PARTITION =
      MessagingIncubatingAttributes.MESSAGING_KAFKA_DESTINATION_PARTITION;
  public static final AttributeKey<String> MESSAGING_KAFKA_MESSAGE_KEY =
      MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_KEY;
  public static final AttributeKey<Long> MESSAGING_KAFKA_MESSAGE_OFFSET =
      MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_OFFSET;
  public static final AttributeKey<Boolean> MESSAGING_KAFKA_MESSAGE_TOMBSTONE =
      MessagingIncubatingAttributes.MESSAGING_KAFKA_MESSAGE_TOMBSTONE;
  public static final AttributeKey<Long> MESSAGING_MESSAGE_BODY_SIZE =
      MessagingIncubatingAttributes.MESSAGING_MESSAGE_BODY_SIZE;
  public static final AttributeKey<String> MESSAGING_MESSAGE_CONVERSATION_ID =
      MessagingIncubatingAttributes.MESSAGING_MESSAGE_CONVERSATION_ID;
  public static final AttributeKey<Long> MESSAGING_MESSAGE_ENVELOPE_SIZE =
      MessagingIncubatingAttributes.MESSAGING_MESSAGE_ENVELOPE_SIZE;
  public static final AttributeKey<String> MESSAGING_MESSAGE_ID =
      MessagingIncubatingAttributes.MESSAGING_MESSAGE_ID;
  public static final AttributeKey<String> MESSAGING_OPERATION =
      MessagingIncubatingAttributes.MESSAGING_OPERATION;
  public static final AttributeKey<String> MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY =
      MessagingIncubatingAttributes.MESSAGING_RABBITMQ_DESTINATION_ROUTING_KEY;
  public static final AttributeKey<String> MESSAGING_ROCKETMQ_CLIENT_GROUP =
      MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_CLIENT_GROUP;
  public static final AttributeKey<String> MESSAGING_ROCKETMQ_CONSUMPTION_MODEL =
      MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_CONSUMPTION_MODEL;
  public static final AttributeKey<Long> MESSAGING_ROCKETMQ_MESSAGE_DELAY_TIME_LEVEL =
      MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_DELAY_TIME_LEVEL;
  public static final AttributeKey<Long> MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP =
      MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_DELIVERY_TIMESTAMP;
  public static final AttributeKey<String> MESSAGING_ROCKETMQ_MESSAGE_GROUP =
      MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_GROUP;
  public static final AttributeKey<List<String>> MESSAGING_ROCKETMQ_MESSAGE_KEYS =
      MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_KEYS;
  public static final AttributeKey<String> MESSAGING_ROCKETMQ_MESSAGE_TAG =
      MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_TAG;
  public static final AttributeKey<String> MESSAGING_ROCKETMQ_MESSAGE_TYPE =
      MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_MESSAGE_TYPE;
  public static final AttributeKey<String> MESSAGING_ROCKETMQ_NAMESPACE =
      MessagingIncubatingAttributes.MESSAGING_ROCKETMQ_NAMESPACE;
  public static final AttributeKey<String> MESSAGING_SYSTEM =
      MessagingIncubatingAttributes.MESSAGING_SYSTEM;

  public static final class MessagingSystemValues {
    public static final String ACTIVEMQ =
        MessagingIncubatingAttributes.MessagingSystemValues.ACTIVEMQ;
    public static final String AWS_SQS =
        MessagingIncubatingAttributes.MessagingSystemValues.AWS_SQS;
    public static final String AZURE_EVENTGRID =
        MessagingIncubatingAttributes.MessagingSystemValues.AZURE_EVENTGRID;
    public static final String AZURE_EVENTHUBS =
        MessagingIncubatingAttributes.MessagingSystemValues.AZURE_EVENTHUBS;
    public static final String AZURE_SERVICEBUS =
        MessagingIncubatingAttributes.MessagingSystemValues.AZURE_SERVICEBUS;
    public static final String GCP_PUBSUB =
        MessagingIncubatingAttributes.MessagingSystemValues.GCP_PUBSUB;
    public static final String JMS = MessagingIncubatingAttributes.MessagingSystemValues.JMS;
    public static final String KAFKA = MessagingIncubatingAttributes.MessagingSystemValues.KAFKA;
    public static final String RABBITMQ =
        MessagingIncubatingAttributes.MessagingSystemValues.RABBITMQ;
    public static final String ROCKETMQ =
        MessagingIncubatingAttributes.MessagingSystemValues.ROCKETMQ;

    private MessagingSystemValues() {}
  }

  public static final class MessagingRocketmqMessageTypeValues {
    public static final String NORMAL =
        MessagingIncubatingAttributes.MessagingRocketmqMessageTypeValues.NORMAL;
    public static final String FIFO =
        MessagingIncubatingAttributes.MessagingRocketmqMessageTypeValues.FIFO;
    public static final String DELAY =
        MessagingIncubatingAttributes.MessagingRocketmqMessageTypeValues.DELAY;
    public static final String TRANSACTION =
        MessagingIncubatingAttributes.MessagingRocketmqMessageTypeValues.TRANSACTION;

    private MessagingRocketmqMessageTypeValues() {}
  }

  public static final class MessagingRocketmqConsumptionModelValues {
    public static final String CLUSTERING =
        MessagingIncubatingAttributes.MessagingRocketmqConsumptionModelValues.CLUSTERING;
    public static final String BROADCASTING =
        MessagingIncubatingAttributes.MessagingRocketmqConsumptionModelValues.BROADCASTING;

    private MessagingRocketmqConsumptionModelValues() {}
  }

  public static final class MessagingOperationValues {
    public static final String PUBLISH =
        MessagingIncubatingAttributes.MessagingOperationValues.PUBLISH;
    public static final String CREATE =
        MessagingIncubatingAttributes.MessagingOperationValues.CREATE;
    public static final String RECEIVE =
        MessagingIncubatingAttributes.MessagingOperationValues.RECEIVE;
    public static final String DELIVER =
        MessagingIncubatingAttributes.MessagingOperationValues.DELIVER;

    private MessagingOperationValues() {}
  }

  public static final AttributeKey<Long> CODE_COLUMN = CodeIncubatingAttributes.CODE_COLUMN;
  public static final AttributeKey<String> CODE_FILEPATH = CodeIncubatingAttributes.CODE_FILEPATH;
  public static final AttributeKey<String> CODE_FUNCTION = CodeIncubatingAttributes.CODE_FUNCTION;
  public static final AttributeKey<Long> CODE_LINENO = CodeIncubatingAttributes.CODE_LINENO;
  public static final AttributeKey<String> CODE_NAMESPACE = CodeIncubatingAttributes.CODE_NAMESPACE;
  public static final AttributeKey<String> CODE_STACKTRACE =
      CodeIncubatingAttributes.CODE_STACKTRACE;

  public static final AttributeKey<String> PEER_SERVICE = PeerIncubatingAttributes.PEER_SERVICE;

  public static final AttributeKey<Long> MESSAGE_COMPRESSED_SIZE =
      MessageIncubatingAttributes.MESSAGE_COMPRESSED_SIZE;
  public static final AttributeKey<Long> MESSAGE_ID = MessageIncubatingAttributes.MESSAGE_ID;
  public static final AttributeKey<String> MESSAGE_TYPE = MessageIncubatingAttributes.MESSAGE_TYPE;
  public static final AttributeKey<Long> MESSAGE_UNCOMPRESSED_SIZE =
      MessageIncubatingAttributes.MESSAGE_UNCOMPRESSED_SIZE;

  public static final class MessageTypeValues {
    public static final String SENT = MessageIncubatingAttributes.MessageTypeValues.SENT;
    public static final String RECEIVED = MessageIncubatingAttributes.MessageTypeValues.RECEIVED;

    private MessageTypeValues() {}
  }

  public static final AttributeKey<Long> THREAD_ID = ThreadIncubatingAttributes.THREAD_ID;
  public static final AttributeKey<String> THREAD_NAME = ThreadIncubatingAttributes.THREAD_NAME;

  public static final AttributeKey<Boolean> EXCEPTION_ESCAPED =
      ExceptionIncubatingAttributes.EXCEPTION_ESCAPED;
  public static final AttributeKey<String> EXCEPTION_MESSAGE =
      ExceptionIncubatingAttributes.EXCEPTION_MESSAGE;
  public static final AttributeKey<String> EXCEPTION_STACKTRACE =
      ExceptionIncubatingAttributes.EXCEPTION_STACKTRACE;
  public static final AttributeKey<String> EXCEPTION_TYPE =
      ExceptionIncubatingAttributes.EXCEPTION_TYPE;

  public static final String EXCEPTION_EVENT_NAME = "exception";

  public static final AttributeKey<String> FAAS_TRIGGER = ResourceAttributes.FAAS_TRIGGER;
  public static final AttributeKey<String> FAAS_INVOCATION_ID =
      ResourceAttributes.FAAS_INVOCATION_ID;

  public static final class FaasTriggerValues {
    private FaasTriggerValues() {}

    public static final String HTTP = ResourceAttributes.FaasTriggerValues.HTTP;
  }

  public static final AttributeKey<String> ENDUSER_ID = EnduserIncubatingAttributes.ENDUSER_ID;
  public static final AttributeKey<String> ENDUSER_ROLE = EnduserIncubatingAttributes.ENDUSER_ROLE;
  public static final AttributeKey<String> ENDUSER_SCOPE =
      EnduserIncubatingAttributes.ENDUSER_SCOPE;
}
