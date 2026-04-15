# search-rpc

`search-rpc` 负责帖子搜索、搜索建议和搜索历史，是系统里对 Elasticsearch 依赖最重的服务。

## 服务职责

- 帖子全文搜索
- 搜索建议词生成
- 搜索历史记录与删除
- 热度排序

## 接口规范

| 网关路径 | 方法 | 说明 |
| --- | --- | --- |
| `/api/post/search` | `GET` | 搜索帖子 |
| `/api/post/search/suggest` | `GET` | 搜索联想词 |
| `/api/search/history` | `GET` | 获取搜索历史 |
| `/api/search/history` | `DELETE` | 清空搜索历史 |
| `/api/search/history/item` | `DELETE` | 删除单条搜索历史 |

调用示例：

```bash
curl 'http://localhost:8090/api/post/search?keyword=test&sort=hot&page=1&size=24'
```

```bash
curl 'http://localhost:8090/api/post/search/suggest?keyword=test'
```

## 技术实现

### 1. Elasticsearch 搜索

搜索请求会根据参数动态构造 ES 查询：

- 关键词使用 `multi_match`
  - `title^3`
  - `title.pinyin^1.5`
  - `content`
  - `tags^2`
  - `tags.pinyin`
- 标签筛选使用 `term`
- `new / old / likes / hot` 使用不同排序策略

### 2. 热度排序

`sort=hot` 时使用 `function_score`：

- `likeCount` 做 `field_value_factor`
- `createdAt` 做高斯衰减
- 最终兼顾热度和时效性

这能避免“老帖永远霸榜”或“新帖全靠发布时间”的极端问题。

### 3. 搜索建议词

建议词基于 ES `match_phrase_prefix` + highlight：

- 中文关键词查 `title / tags`
- 非中文关键词查 `title.pinyin / tags.pinyin`
- 结果做去重和顺序保持
- 采用“原词优先但不无脑插入”的策略
  - 有真实候选时，才优先放原词
  - 如果只有原词本身，没有其它候选，则返回空数组

### 4. 搜索历史

- 用户搜索时异步写入 Mongo `search_histories`
- 保留最近搜索记录
- 支持全清或按关键词删除

## 技术亮点

- 搜索和建议词都兼顾了中文和拼音匹配
- `hot` 排序不是简单按点赞数，而是用 `function_score` 做综合热度模型
- 搜索历史写入异步化，不阻塞主搜索请求
- 搜索服务只返回“搜索结果”，实时互动状态由网关再去 interaction-rpc 补齐，职责边界清楚

## 关键依赖

- `Elasticsearch`
- `MongoDB`
- `gateway-api`

## 本地运行

```bash
cd services/search-rpc
go build ./...
go test ./...
```

通过 Docker 启动：

```bash
docker compose up -d --build search-rpc
```
