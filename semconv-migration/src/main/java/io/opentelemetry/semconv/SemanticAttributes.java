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
import io.opentelemetry.semconv.incubating.MessageIncubatingAttributes;
import io.opentelemetry.semconv.incubating.MessagingIncubatingAttributes;
import io.opentelemetry.semconv.incubating.PeerIncubatingAttributes;
import io.opentelemetry.semconv.incubating.RpcIncubatingAttributes;
import io.opentelemetry.semconv.incubating.ThreadIncubatingAttributes;
import java.util.List;

public class SemanticAttributes {

  private SemanticAttributes() {}

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
    public static final String MYSQL = DbIncubatingAttributes.DbSystemValues.MYSQL;
    public static final String ORACLE = DbIncubatingAttributes.DbSystemValues.ORACLE;
    public static final String DB2 = DbIncubatingAttributes.DbSystemValues.DB2;
    public static final String POSTGRESQL = DbIncubatingAttributes.DbSystemValues.POSTGRESQL;
    public static final String HANADB = DbIncubatingAttributes.DbSystemValues.HANADB;
    public static final String DERBY = DbIncubatingAttributes.DbSystemValues.DERBY;
    public static final String MARIADB = DbIncubatingAttributes.DbSystemValues.MARIADB;
    public static final String H2 = DbIncubatingAttributes.DbSystemValues.H2;
    public static final String CASSANDRA = DbIncubatingAttributes.DbSystemValues.CASSANDRA;
    public static final String MONGODB = DbIncubatingAttributes.DbSystemValues.MONGODB;
    public static final String REDIS = DbIncubatingAttributes.DbSystemValues.REDIS;
    public static final String COUCHBASE = DbIncubatingAttributes.DbSystemValues.COUCHBASE;
    public static final String GEODE = DbIncubatingAttributes.DbSystemValues.GEODE;
    public static final String ELASTICSEARCH = DbIncubatingAttributes.DbSystemValues.ELASTICSEARCH;
    public static final String OPENSEARCH = DbIncubatingAttributes.DbSystemValues.OPENSEARCH;

    private DbSystemValues() {}
  }

  public static final AttributeKey<String> RPC_METHOD = RpcIncubatingAttributes.RPC_METHOD;
  public static final AttributeKey<String> RPC_SERVICE = RpcIncubatingAttributes.RPC_SERVICE;
  public static final AttributeKey<String> RPC_SYSTEM = RpcIncubatingAttributes.RPC_SYSTEM;
  public static final AttributeKey<Long> RPC_GRPC_STATUS_CODE =
      RpcIncubatingAttributes.RPC_GRPC_STATUS_CODE;

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
  public static final AttributeKey<String> MESSAGING_SYSTEM =
      MessagingIncubatingAttributes.MESSAGING_SYSTEM;

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

  public static final AttributeKey<String> CODE_FILEPATH = CodeIncubatingAttributes.CODE_FILEPATH;
  public static final AttributeKey<String> CODE_FUNCTION = CodeIncubatingAttributes.CODE_FUNCTION;
  public static final AttributeKey<Long> CODE_LINENO = CodeIncubatingAttributes.CODE_LINENO;
  public static final AttributeKey<String> CODE_NAMESPACE = CodeIncubatingAttributes.CODE_NAMESPACE;

  public static final AttributeKey<String> PEER_SERVICE = PeerIncubatingAttributes.PEER_SERVICE;

  public static final AttributeKey<Long> MESSAGE_ID = MessageIncubatingAttributes.MESSAGE_ID;
  public static final AttributeKey<String> MESSAGE_TYPE = MessageIncubatingAttributes.MESSAGE_TYPE;

  public static final class MessageTypeValues {
    public static final String SENT = MessageIncubatingAttributes.MessageTypeValues.SENT;
    public static final String RECEIVED = MessageIncubatingAttributes.MessageTypeValues.RECEIVED;

    private MessageTypeValues() {}
  }

  public static final AttributeKey<Long> THREAD_ID = ThreadIncubatingAttributes.THREAD_ID;
  public static final AttributeKey<String> THREAD_NAME = ThreadIncubatingAttributes.THREAD_NAME;

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
