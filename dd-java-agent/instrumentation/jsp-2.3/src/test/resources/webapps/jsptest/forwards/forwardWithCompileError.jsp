<jsp:forward page = "../common/loop.jsp" />
<html>
   <head>
      <title>FORWARD WITH COMPILE ERROR</title>
   </head>
   <%
      FakeNonExistentClass fec = new FakeNonExistentClass()
   %>
   <body>
    <h1>BYE!</h1>
   </body>
</html>
