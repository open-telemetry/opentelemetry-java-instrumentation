<html>
  <head><title>BASIC JSP</title></head>
    <body>
      <%
        for (int i = 0; i < 3; ++i) {
      %>
        <h2>number:<%= i %></h2><p></p>
      <%
        }
      %>
  </body>
</html>
