# Codex Download Zone

`/codex` 的下载区用于放置需要从手机端取回的最终产物，比如 `.md`、`.pdf`、`.pptx`、导出压缩包。

## 存储布局

- 服务器默认根目录：`/srv/home/.storage/codex-downloads`
- 文件目录：`/srv/home/.storage/codex-downloads/files`
- 清单文件：`/srv/home/.storage/codex-downloads/index.json`

`index.json` 每条记录包含：

- `id`
- `filename`
- `contentType`
- `size`
- `sha256`
- `sourceThreadId`
- `sourceThreadName`
- `createdAt`
- `createdBy`
- `label`
- `notes`

文件名在磁盘上的命名规则为：

- `<artifactId>-<sanitizedFilename>`

## 鉴权访问

以下接口都由 `codex-web` 暴露，并沿用 `/codex` 现有 cookie 鉴权：

- `GET /api/codex/downloads`
- `GET /api/codex/downloads/[artifactId]`
- `GET /api/codex/downloads/[artifactId]/content`

下载内容不会暴露真实磁盘路径，只通过受保护路由流式返回。

## 本机上传脚本

在 `codex-web` 仓库根目录执行：

```bash
node scripts/upload-codex-artifact.mjs --file /absolute/path/to/output.pdf --label "Weekly export"
```

常用参数：

```bash
node scripts/upload-codex-artifact.mjs \
  --file /absolute/path/to/output.pdf \
  --label "Lease weekly export" \
  --source-thread-id thread_123 \
  --source-thread-name "运维服务" \
  --notes "final reviewed export"
```

辅助参数：

- `--dry-run`：只计算元数据和远端落点，不执行上传
- `--target`：覆盖默认 SSH 目标，默认 `1服务器`
- `--remote-root`：覆盖远端根目录，默认 `/srv/home/.storage/codex-downloads`
- `--created-by`：写入操作者标记，默认 `codex-local`

## 运维边界

- 只上传用户明确要求保留/下载的最终产物。
- 不要把整个工作目录、敏感配置、原始凭据或无关中间文件丢进下载区。
- 如果对路径、标签或线程元数据不确定，先跑一次 `--dry-run`。
