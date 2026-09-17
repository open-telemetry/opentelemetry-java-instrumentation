<%@ page import="javax.naming.InitialContext,javax.sql.DataSource" %>
<%
  DataSource dataSource =
      (DataSource) new InitialContext().lookup("java:comp/env/jdbc/TestDB");
  out.print(dataSource.getClass().getName());
%>
