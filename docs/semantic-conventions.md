# Semantic conventions

This document describes which [OpenTelemetry Semantic Conventions](https://github.com/open-telemetry/opentelemetry-specification/tree/master/specification/trace/semantic_conventions)
are implemented by Java autoinstrumentation and which ones are not.

## Http Server

| Attribute | Required | Implemented? |
|---|:---:|:---:|
| `http.method` | Y | + |
| `http.url` | N | - [1] |
| `http.target` | N | + [1] |
| `http.host` | N | + [1] |
| `http.scheme` | N | + [1] |
| `http.status_code` | Y | + |
| `http.flavor` | N | + [2] |
| `http.user_agent` | N | + |
| `http.request_content_length` | N | - |
| `http.request_content_length_uncompressed` | N | - |
| `http.response_content_length` | N | - |
| `http.response_content_length_uncompressed` | N | - |
| `http.server_name` | N | - |
| `http.route` | N | - |
| `http.client_ip` | N | + |

**[1]:** Most server instrumentation captures `http.scheme`, `http.host`, and `http.target`
(and does not capture `http.url`).
Netty instrumentation is currently the only exception to this rule. Netty instrumentation
captures `http.url` (and does not capture `http.scheme`, `http.host`, or `http.target`).

As the majority of Java frameworks don't provide a standard way to obtain "The full request
target as passed in a HTTP request line or equivalent.", we don't set `http.target` semantic
attribute. As either it or `http.url` is required, we set the latter. This, in turn, makes setting
`http.schema` and `http.host` unnecessary duplication. Therefore, we do not set them as well.

**[2]:** In case of Armeria, return values are [SessionProtocol](https://github.com/line/armeria/blob/master/core/src/main/java/com/linecorp/armeria/common/SessionProtocol.java),
not values defined by spec.


## Http Client

| Attribute | Required | Implemented? |
|---|:---:|:---:|
| `http.method` | Y | + |
| `http.url` | N | + |
| `http.target` | N | - [1] |
| `http.host` | N | - [1] |
| `http.scheme` | N | - [1] |
| `http.status_code` | Y | + |
| `http.flavor` | N | + [2] |
| `http.user_agent` | N | + |
| `http.request_content_length` | N | - |
| `http.request_content_length_uncompressed` | N | - |
| `http.response_content_length` | N | - |
| `http.response_content_length_uncompressed` | N | - |

**[1]:** `http.scheme`, `http.host` and `http.target` are unnecessary since `http.url` is captured.

**[2]:** In case of Armeria, return values are [SessionProtocol](https://github.com/line/armeria/blob/master/core/src/main/java/com/linecorp/armeria/common/SessionProtocol.java),
not values defined by spec.

## RPC

| Attribute | Required | Implemented? |
| -------------- | :---: | :---: |
| `rpc.system`   | Y | + |
| `rpc.service`  | N | + |
| `rpc.method`   | N | + |

## Database

| Attribute | Required | Implemented? |
| -------------- | :---: | :---: |
| `db.system`   | Y | + |
| `db.connection_string`  | N | only set for Redis, JDBC and MongoDB |
| `db.user`   | N | only set for JDBC|
| `db.jdbc.driver_classname`   | N | - |
| `db.mssql.instance_name`   | N | - |
| `db.name`   | N | only set of JDBC, Mongo, Geode and MongoDB |
| `db.statement`   | N | +, except for ElasticSearch and Memcached, see `db.operation` |
| `db.operation`   | N | only set for ElasticSearch, Memcached and JDBC |
| `db.cassandra.keyspace`   | Y | + |
| `db.hbase`   | Y | -, HBase is not supported |
| `db.redis.database_index`   | N | only set for Lettuce driver, not for Jedis |
| `db.mongodb.collection`   | Y | - |

## Messaging

 Attribute name |  Required? | Implemented? |
| -------------- |  :-----: | :---: |
| `messaging.system` |  Y | + |
| `messaging.destination` | Y | + |
| `messaging.destination_kind` | Y | + |
| `messaging.temp_destination` | N | - |
| `messaging.protocol` | N | - |
| `messaging.protocol_version` | N | - |
| `messaging.url` | N | - |
| `messaging.message_id` | N | only for JMS |
| `messaging.conversation_id` | N | only for JMS |
| `messaging.message_payload_size_bytes` | N | only for RabbitMQ and Kafka [1] |
| `messaging.message_payload_compressed_size_bytes` | N | - |
| `messaging.operation` | for consumers only | +

**[1]:** Kafka consumer instrumentation sets this to the serialized size of the value
