<html>
<head><title>GET QUERY JSP</title></head>
<body>
  <%
    String query = request.getQueryString();
  %>
      <h2><%= query %></h2><p></p>
  <%
    if (query.equals("HELLO")) {
      out.print("WORLD");
    }
  %>
</body>
</html>
