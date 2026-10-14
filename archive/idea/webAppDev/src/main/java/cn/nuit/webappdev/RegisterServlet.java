package cn.nuit.webappdev;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.sql.*;

@WebServlet("/register")
public class RegisterServlet extends HttpServlet {

    private static String DB_URL;

    @Override
    public void init() throws ServletException {
        try {
            // 动态定位数据库路径：项目根目录
            String dbPath = System.getProperty("user.dir") + File.separator + "users.db";
            DB_URL = "jdbc:sqlite:" + dbPath;
            System.out.println("Database path: " + dbPath);

            // 加载驱动（某些环境必须手动）
            Class.forName("org.sqlite.JDBC");

            // 创建数据库表
            try (Connection conn = DriverManager.getConnection(DB_URL);
                 Statement stmt = conn.createStatement()) {
                String sql = "CREATE TABLE IF NOT EXISTS users (" +
                        "id INTEGER PRIMARY KEY AUTOINCREMENT," +
                        "username TEXT UNIQUE NOT NULL," +
                        "password TEXT NOT NULL)";
                stmt.execute(sql);
            }

            System.out.println("数据库初始化成功");
        } catch (Exception e) {
            e.printStackTrace();
            throw new ServletException("数据库初始化失败", e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        String username = req.getParameter("username");
        String password = req.getParameter("password");

        try (Connection conn = DriverManager.getConnection(DB_URL);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO users(username, password) VALUES(?, ?)")) {
            ps.setString(1, username);
            ps.setString(2, password);
            ps.executeUpdate();
            req.setAttribute("message", "注册成功，请登录！");
            req.getRequestDispatcher("index.jsp").forward(req, resp);
        } catch (SQLException e) {
            if (e.getMessage().contains("UNIQUE")) {
                req.setAttribute("error", "该用户名已存在！");
            } else {
                req.setAttribute("error", "注册失败：" + e.getMessage());
            }
            req.getRequestDispatcher("register.jsp").forward(req, resp);
        }
    }
}