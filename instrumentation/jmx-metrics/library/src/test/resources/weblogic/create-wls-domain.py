# Copyright The OpenTelemetry Authors
# SPDX-License-Identifier: Apache-2.0

import os

domain_name = os.environ.get('DOMAIN_NAME', 'base_domain')
admin_name = os.environ.get('ADMIN_NAME', 'AdminServer')
admin_listen_port = int(os.environ.get('ADMIN_LISTEN_PORT', '7001'))
domain_path = '/u01/oracle/user_projects/domains/%s' % domain_name
production_mode = os.environ.get('PRODUCTION_MODE', 'dev')

readTemplate('/u01/oracle/wlserver/common/templates/wls/wls.jar')
set('Name', domain_name)
setOption('DomainName', domain_name)

cd('/Servers/AdminServer')
set('Name', admin_name)
set('ListenAddress', '')
set('ListenPort', admin_listen_port)

cd(('/Security/%s/User/weblogic') % domain_name)
cmo.setName(username)
cmo.setPassword(password)

cd('/')
create('ExampleDS', 'JDBCSystemResource')
cd('/JDBCSystemResource/ExampleDS/JdbcResource/ExampleDS')
set('Name', 'ExampleDS')

create('myJdbcDataSourceParams', 'JDBCDataSourceParams')
cd('JDBCDataSourceParams/NO_NAME_0')
set('JNDINames', java.lang.String('jdbc/ExampleDS'))
set('GlobalTransactionsProtocol', 'OnePhaseCommit')

cd('/JDBCSystemResource/ExampleDS/JdbcResource/ExampleDS')
create('myJdbcDriverParams', 'JDBCDriverParams')
cd('JDBCDriverParams/NO_NAME_0')
set('DriverName', 'org.apache.derby.jdbc.EmbeddedDriver')
set('URL', 'jdbc:derby:memory:testdb;create=true')

create('myProps', 'Properties')
cd('Properties/NO_NAME_0')
create('user', 'Property')
cd('Property/user')
set('Value', 'app')

cd('/JDBCSystemResource/ExampleDS/JdbcResource/ExampleDS')
create('myJdbcConnectionPoolParams', 'JDBCConnectionPoolParams')
cd('JDBCConnectionPoolParams/NO_NAME_0')
set('InitialCapacity', 1)
set('MaxCapacity', 5)
set('TestTableName', 'SQL SELECT 1 FROM SYSIBM.SYSDUMMY1')

cd('/')
assign('JDBCSystemResource', 'ExampleDS', 'Target', admin_name)

setOption('OverwriteDomain', 'true')
setOption('ServerStartMode', production_mode)

writeDomain(domain_path)
closeTemplate()
exit()
