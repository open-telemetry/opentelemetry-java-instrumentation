### Notes and well-known identifiers for `db.system`

This is a non-exhaustive list of well-known identifiers to be specified for `db.system`.

If a value defined in this list applies to the DBMS to which the request is sent, this value MUST be used.
If no value defined in this list is suitable, a custom value MUST be provided.
This custom value MUST be the name of the DBMS in lowercase and without a version number to stay consistent with existing identifiers.

It is encouraged to open a PR towards this specification to add missing values to the list, especially when instrumentations for those missing databases are written.
This allows multiple instrumentations for the same database to be aligned and eases analyzing for backends.

The value `other_sql` is intended as a fallback and MUST only be used if the DBMS is known to be SQL-compliant but the concrete product is not known to the instrumentation.
If the concrete DBMS is known to the instrumentation, its specific identifier MUST be used.

| Value for `db.system` | Product name              | Note                           |
| :-------------------- | :------------------------ | :----------------------------- |
| `"as400"`             | IBM AS400 Database        | not on [database.md]           |
| `"cassandra"`         | Cassandra                 |                                |
| `"cosmosdb"`          | Microsoft Azure Cosmos DB |                                |
| `"couchbase"`         | Couchbase                 |                                |
| `"couchdb"`           | CouchDB                   |                                |
| `"db2"`               | IBM Db2                   |                                |
| `"derby"`             | Apache Derby              |                                |
| `"dynamodb"`          | Amazon DynamoDB           |                                |
| `"h2"`                | H2 Database               | not on [database.md]           |
| `"hbase"`             | HBase                     |                                |
| `"hive"`              | Apache Hive               |                                |
| `"hsqldb"`            | Hyper SQL Database        | not on [database.md]           |
| `"mariadb"`           | MariaDB                   |                                |
| `"mongodb"`           | MongoDB                   |                                |
| `"mssql"`             | Microsoft SQL Server      |                                |
| `"mysql"`             | MySQL                     |                                |
| `"neo4j"`             | Neo4j                     |                                |
| `"oracle"`            | Oracle Database           |                                |
| `"other_sql"`         | Some other SQL Database   | Fallback only. See note above. |
| `"postgresql"`        | PostgreSQL                |                                |
| `"redis"`             | Redis                     |                                |
| `"sap"`               | SAP                       | not on [database.md]           |
| `"sqlite"`            | SQLite                    |                                |
| `"teradata"`          | Teradata                  |                                |

[database.md]: https://github.com/open-telemetry/opentelemetry-specification/blob/master/specification/trace/semantic_conventions/database.md
