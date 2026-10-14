# Cinema System Web App

一个基于 Vue 3 和 Node.js实现的前后端分离的电影购票系统。支持查看热映电影、影院排片、用户下单、排行榜等核心功能。

## 启动方式

### 前提：

- 安装 Node.js（建议版本 ≥ 16）
- 本地安装 `MySQL`（如果测试使用本地数据库）
- 推荐使用 [Railway](https://railway.app) 部署云端 MySQL 数据库


### 1. 后端启动

```bash
cd backend
npm install
node bin/www
```
### 2. 前端启动

```bash
cd frontend
npm install
npm run serve
```
## 数据库说明

数据库结构已包含以下核心表：
topmovie、hotmovie、hopemovie：电影。cinemalist、schedule_list：影院与排片。orderlist：用户订单记录。user：用户数据


## 技术栈
 • 前端：Vue 3 + Vue Router + SCSS
 
 • 后端：Node.js + Express + MySQL2


