<%--
  Created by IntelliJ IDEA.
  User: friedrichkamron
  Date: 2025/10/16
  Time: 14:59
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html;charset=UTF-8" %>
<%
  String user = (String) session.getAttribute("user");
  if (user == null) {
    response.sendRedirect("index.jsp");
    return;
  }
%>
<html>
<body>
<h2>欢迎，<%= user %>！</h2>
<a href="index.jsp">返回登录页</a >
</body>
</html>
