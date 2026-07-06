# Security Policy

## 支持范围

这个仓库是学习和课程设计项目，当前只维护主分支中的最新代码。

## 报告问题

如果你发现敏感信息泄露、登录校验绕过、SQL 注入或其他安全问题，请通过 GitHub Issue 说明问题影响和复现步骤。请不要在 Issue 中公开真实密码、token 或私钥。

## 本地配置

数据库连接密码应通过 `DB_PASSWORD` 环境变量提供，不应写入 `application.properties` 或提交到仓库。
