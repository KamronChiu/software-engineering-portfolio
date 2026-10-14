<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<!DOCTYPE html>
<html>
<head>
    <meta charset="UTF-8">
    <title>登录页面</title>
    <link rel="stylesheet" href="css/style.css">
</head>
<body>
<div class="login-box">
    <h2>登录系统</h2>
    <form action="login" method="post">
        <label>用户名：</label><br>
        <input type="text" name="username" required><br>
        <label>密码：</label><br>
        <input type="password" name="password" required><br>
        <button type="submit">登录</button>
    </form>
    <p style="text-align:center;margin-top:10px;">
        <a href="register.jsp">还没有账号？去注册</a >
    </p >
    <div class="error">
        ${error}
    </div>
</div>
</body>
</html>