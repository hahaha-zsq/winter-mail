<div align="center">
  <img src="frontend/public/favicon.svg" width="72" height="72" alt="Winter Mail 图标" />
  <h1>Winter Mail</h1>
  <p>一个基于 Spring Boot 与 React 的多邮箱客户端</p>
  <p>
    <img src="https://img.shields.io/badge/Java-17-70513b?style=flat-square" alt="Java 17" />
    <img src="https://img.shields.io/badge/Spring%20Boot-2.6-6b8e5b?style=flat-square" alt="Spring Boot 2.6" />
    <img src="https://img.shields.io/badge/React-18-3c7190?style=flat-square" alt="React 18" />
    <img src="https://img.shields.io/badge/MySQL-Database-466b7d?style=flat-square" alt="MySQL" />
  </p>
</div>

Winter Mail 面向个人使用：将 Gmail、QQ、网易 163/126 和自定义 IMAP/SMTP 邮箱集中到同一个收件箱，并允许选择任意已添加账号发送邮件。后端使用 Java 17、Spring Boot 2.6、MyBatis-Plus 与 MySQL；前端使用 React 18 与 Vite 5。

## 目录

- [✨ 主要功能](#features)
- [🗂️ 项目结构](#structure)
- [📮 自定义发送 starter](#starter)
- [🚀 快速开始](#quick-start)
- [🧭 使用流程](#usage)
- [🔌 API 概览](#api)
- [🧪 测试与构建](#testing)
- [📌 当前使用边界](#limitations)
- [🤝 参与贡献](#contributing)

<a id="features"></a>

## ✨ 主要功能

| 功能 | 说明 |
| --- | --- |
| 多账号收件 | 按账号读取 IMAP 邮件并按日期合并；默认每次展示 20 封，滚动到底部继续加载。 |
| 邮件详情 | 点击邮件后读取完整纯文本或 HTML 正文；只有 HTML 正文时也会生成可读摘要。 |
| 附件查看 | 展示收到的附件名称与大小，并提供下载入口；单个下载上限为 25 MB。 |
| 灵活发信 | 纯文本/HTML、抄送、附件和最多 100 位主收件人的逐人批量发送。 |
| 账号安全 | 邮箱授权码用 AES-GCM 加密后保存到 MySQL，账号列表接口不返回授权码。 |
| 前端界面 | 响应式布局、浅色/深色主题，以及浏览器标签页图标。 |

<a id="structure"></a>

## 🗂️ 项目结构

```text
winter-mail/
├── pom.xml                             # Java 依赖与构建配置
├── src/main/java/com/winter/wintermail/
│   ├── WinterMailApplication.java      # Spring Boot 入口
│   └── mail/
│       ├── MailApi.java                 # 账号、收件、附件和发信接口
│       ├── AccountRepository.java       # MyBatis-Plus 数据访问
│       ├── CredentialCipher.java        # 授权码加解密
│       ├── ProviderStrategyRegistry.java # 邮箱厂商配置策略
│       ├── MailContentExtractor.java    # MIME 正文和附件解析
│       └── AccountMailSender.java        # 自定义 starter 发信
├── src/main/resources/application.yaml # 后端运行配置
└── frontend/
    ├── src/main.jsx                     # React 界面与交互
    ├── src/inboxPager.js                # 多账号邮件分页合并
    ├── src/api.js                       # API 请求封装
    └── public/favicon.svg
```

<a id="starter"></a>

## 📮 自定义发送 starter

发送功能使用 `io.github.hahaha-zsq:winter-mail-spring-boot-starter:0.0.1`，并为所选账号创建独立的 `JavaMailSender`。

- 无附件纯文本：调用 `WinterMailTemplate.sendMail`。
- HTML 正文及附件：调用 `WinterMailTemplate.sendTemplateEmail`。
- 纯文本加附件：由于当前 starter 的纯文本方法没有附件参数，使用 Spring Mail 生成 `text/plain` MIME 邮件。
- 批量发送：每位主收件人收到独立邮件；若填写抄送，同一组抄送地址会出现在每封邮件中。
- 附件限制：最多 10 个，单个不超过 10 MB，总计不超过 25 MB。有附件时使用 `multipart/form-data`，否则使用 JSON。

若 Maven 仓库暂时无法获取这个 starter，可在项目根目录执行下面的本地安装命令，再构建本项目：

```bash
mvn org.apache.maven.plugins:maven-install-plugin:2.5.2:install-file \
  -Dfile=../starter/winter-mail-spring-boot-starter/target/winter-mail-spring-boot-starter-0.0.1.jar \
  -DpomFile=../starter/winter-mail-spring-boot-starter/pom.xml \
  -DgeneratePom=false
```

<a id="quick-start"></a>

## 🚀 快速开始

需要 JDK 17 或更新版本、Maven 3.8 或更新版本、Node.js/npm，以及可访问的 MySQL 与邮箱 IMAP/SMTP 服务。自定义 starter 需可从 Maven 仓库获取，或按上节说明安装到本机。

MySQL 默认连接地址为 `mysql.winter-cloud.xyz:4000`，数据库名为 `winter_mail`，用户名默认为 `root`。服务首次读写账号时会创建数据库（连接参数启用 `createDatabaseIfNotExist`）及 `mail_accounts` 表，因此数据库用户需要建库和建表权限。配置以下环境变量：

```bash
export MAIL_DB_PASSWORD='你的 MySQL 密码'
export MAIL_CREDENTIAL_KEY='之前生成并保存的 Base64 密钥'
mvn spring-boot:run
```

**请将 `MAIL_CREDENTIAL_KEY` 保存到安全的密码管理器，并在每次启动时使用同一个值。** 它用于 AES-GCM 加密邮箱授权码；丢失或更换后，已保存的授权码无法解密。也可通过 `MAIL_DB_URL` 和 `MAIL_DB_USER` 覆盖默认连接设置。不要将真实密码或密钥提交到仓库。

首次使用可运行 `openssl rand -base64 32` 生成密钥，保存输出后再填入上面的启动命令。

`MAIL_DB_URL` 是完整 JDBC URL，`MAIL_DB_USER` 是数据库用户名，`MAIL_DB_PASSWORD` 是数据库密码。配置文件目前包含开发用默认值；在共享或部署环境中应显式覆盖密码和密钥。

另开终端启动前端：

```bash
cd frontend
npm install
npm run dev
```

打开 <http://localhost:5173>。生产环境执行 `cd frontend && npm run build`，再将 `frontend/dist` 部署在同源站点并代理 `/api` 到后端。

收信使用加密的 IMAP（IMAPS，默认端口 993），发信使用 SMTP（SSL 或 STARTTLS）；当前不使用 POP3。

网易 163/126 收件箱连接会在认证后发送 IMAP `ID` 命令，以满足其客户端识别要求。

Gmail 通常需要启用 IMAP 并使用应用密码；QQ、网易邮箱通常需要在邮箱设置中开启 IMAP/SMTP 并生成授权码。实际可用方式以服务商当前政策为准。不同厂商的连接参数由 `ProviderStrategyRegistry` 分发，自定义邮箱使用用户输入的服务器参数。

此版本面向本机单人使用；后端默认只监听 `127.0.0.1`，尚无用户身份认证，不要直接暴露到公网。批量发送对每位收件人分别发送并返回成功和失败名单；请遵守服务商限额。

<a id="usage"></a>

## 🧭 使用流程

1. 在“邮箱账号”选择厂商，填写邮箱地址和服务商生成的授权码或应用密码。自定义邮箱需要填写 IMAP/SMTP 主机与端口。
2. 在“收件箱”查看所有账号的来信，按账号筛选或滚动加载更多。点击邮件可读取正文和附件列表。
3. 在“写邮件”选择发件账号，填写主收件人、可选抄送、主题、正文格式及附件后发送。

<a id="api"></a>

## 🔌 API 概览

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| `GET` | `/api/accounts` | 查询账号列表，不返回授权码。 |
| `POST` | `/api/accounts` | 添加邮箱账号。 |
| `DELETE` | `/api/accounts/{id}` | 删除本地账号配置，不删除邮箱服务器上的邮件。 |
| `GET` | `/api/accounts/{id}/messages?limit=20&offset=0` | 获取指定账号的邮件分页；`limit` 最大为 30。 |
| `GET` | `/api/accounts/{id}/messages/{messageId}` | 获取完整正文与附件元数据。 |
| `GET` | `/api/accounts/{id}/messages/{messageId}/attachments/{index}` | 下载指定附件。 |
| `POST` | `/api/send` | 发信；无附件使用 JSON，有附件使用 `multipart/form-data`。 |

收件分页响应为 `{ "messages": [...], "hasMore": true }`。发信请求的 `format` 为 `text` 或 `html`，省略时使用纯文本；响应包含 `sent` 成功列表和 `failed` 失败原因映射。前端收到多个账号的邮件后按日期合并，每次最多向列表追加 20 封。

<a id="testing"></a>

## 🧪 测试与构建

在项目根目录执行后端测试；前端测试和构建在 `frontend` 目录执行：

```bash
mvn test
cd frontend
npm test
npm run build
```

构建产物位于 `frontend/dist`。部署时需要把 `/api` 代理到后端；开发环境已在 Vite 中配置代理。

<a id="limitations"></a>

## 📌 当前使用边界

收件分页依据 IMAP 邮件序号：如果翻页期间邮箱新增或删除邮件，序号可能变化，刷新收件箱可重新从第一页读取。前端搜索只覆盖已经加载的邮件。HTML 邮件显示在隔离的 iframe 中，脚本和外部网络资源不可用。

<a id="contributing"></a>

## 🤝 参与贡献

欢迎通过 Issue 反馈可复现的问题，或提交 Pull Request 改进功能。提交代码前，请运行上面的后端测试、前端测试和生产构建；涉及邮箱厂商行为的改动，请在说明中注明使用的厂商与验证方式。仓库目前没有附带开源许可证，复用或分发前请先与项目维护者确认许可范围。
