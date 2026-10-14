const mysql = require('mysql2')

require('dotenv').config();
//  建立与 mysql 数据库的连接
const db = mysql.createPool({
    host: process.env.DB_HOST, //'localhost'
    port: process.env.DB_PORT,  //原无
    user: process.env.DB_USER, // 登录数据库的账号
    password: process.env.DB_PASSWORD, // supplied via environment
    database: process.env.DB_NAME, // 指定要操作哪个数据库
    waitForConnections: true,
    connectionLimit: 10,
    queueLimit: 0
})

// 传入sql语句
function exec(sql) {
    return new Promise((resolve, reject) => {
        db.getConnection((err, connection) => {
            if (err) {
                console.log(err, "数据库连接失败");
                reject(err);
            } else {
                console.log("数据库连接成功");
                connection.query(sql, (err, result) => {
                    if (err) {
                        reject(err);
                    } else {
                        resolve(result);
                    }
                });
            }
            connection.release();  // 释放连接池
        });
    });
}


// 封装
module.exports = {
    exec, // 最后将方法返回
    escape: mysql.escape // 该方法是保护sql语句插件，一起返回
}
