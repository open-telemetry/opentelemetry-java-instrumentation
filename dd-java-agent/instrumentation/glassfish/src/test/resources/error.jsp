<%@ page contentType="text/plain;charset=UTF-8" isErrorPage="true" trimDirectiveWhitespaces="true" %>

<% if (exception != null) {%>
  <%= exception.getMessage() %>
<% } else { %>
  <%= request.getAttribute("javax.servlet.error.message") %>
<% } %>
