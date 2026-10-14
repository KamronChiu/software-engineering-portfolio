<%--
  Created by IntelliJ IDEA.
  User: friedrichkamron
  Date: 2025/10/16
  Time: 15:12
  To change this template use File | Settings | File Templates.
--%>
<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>注册页面</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
<div class="login-box">
    <h2>用户注册</h2>
    <form action="register" method="post">
        <label>用户名：</label><br>
        <input type="text" name="username" required><br>
        <label>密码：</label><br>
        <input type="password" name="password" required><br>
        <button type="submit">注册</button>
    </form>

    <div class="error">${error}</div>
    <div class="success">${message}</div>

    <p style="text-align:center;margin-top:10px;">
        <a href="index.jsp">返回登录</a >
    </p >
</div>
</body>
</html>
