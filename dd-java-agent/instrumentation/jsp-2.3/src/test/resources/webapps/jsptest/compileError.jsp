<html>
<head><title>COMPILE ERROR JSP</title></head>
<body>
  <%
    FakeClassThatDontExist thingyWithNoSemiColon = abcd
  %>
  <h2>This will fail</h2>
</body>
</html>
