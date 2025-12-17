package com.szu.afternoon3.platform.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.search.Suggester;
import co.elastic.clients.json.JsonData;
import com.szu.afternoon3.platform.common.UserContext;
import com.szu.afternoon3.platform.config.RabbitConfig;
import com.szu.afternoon3.platform.dto.PostUpdateDTO;
import com.szu.afternoon3.platform.entity.mongo.PostCollectDoc;
import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.entity.mongo.PostLikeDoc;
import com.szu.afternoon3.platform.entity.mongo.UserFollowDoc;
import com.szu.afternoon3.platform.event.PostCreateEvent;
import com.szu.afternoon3.platform.event.PostDeleteEvent;
import com.szu.afternoon3.platform.event.PostUpdateEvent;
import com.szu.afternoon3.platform.event.UserSearchEvent;
import com.szu.afternoon3.platform.exception.AppException;
import com.szu.afternoon3.platform.enums.ResultCode;
import com.szu.afternoon3.platform.repository.*;
import com.szu.afternoon3.platform.service.PostService;
import com.szu.afternoon3.platform.service.UserService;

import com.szu.afternoon3.platform.vo.PostVO;
import com.szu.afternoon3.platform.vo.UserInfo;
import com.szu.afternoon3.platform.dto.PostCreateDTO;
import com.szu.afternoon3.platform.entity.User;
import com.szu.afternoon3.platform.mapper.UserMapper;
import com.szu.afternoon3.platform.entity.mongo.SearchHistoryDoc;
import com.szu.afternoon3.platform.repository.SearchHistoryRepository;
import lombok.extern.slf4j.Slf4j;
import lombok.extern.slf4j.XSlf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.UncategorizedElasticsearchException;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.data.elasticsearch.core.suggest.response.Suggest;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.szu.afternoon3.platform.entity.es.PostEsDoc;
// 注意引入正确的 Spring Data ES 依赖
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PostServiceImpl implements PostService {

    @Autowired
    private PostRepository postRepository;
    @Autowired
    private UserFollowRepository userFollowRepository;
    @Autowired
    private PostLikeRepository postLikeRepository;
    @Autowired
    private PostCollectRepository postCollectRepository;
    @Autowired
    private PostRatingRepository postRatingRepository;
    @Autowired
    private MongoTemplate mongoTemplate;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private SearchHistoryRepository searchHistoryRepository;
    @Autowired
    private StringRedisTemplate redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ElasticsearchOperations esOperations; // 注入 ES 操作模板

    @Override
    public Map<String, Object> getPostList(Integer page, Integer size, String tab, String tag, String sort) {
        // 1. 处理分页参数
        int pageNum = (page == null || page < 1) ? 0 : page - 1;
        int pageSize = (size == null || size < 1) ? 20 : size;

        // =================================================================================
        // 【新增逻辑】路由分发：如果是通用"热门"排序，直接交给 ES 处理
        // =================================================================================
        // 条件：sort是hot + 不是关注流 + 不是查特定标签
        if ("hot".equalsIgnoreCase(sort) && !"follow".equalsIgnoreCase(tab) && StrUtil.isBlank(tag)) {
            // 直接调用 searchPosts，关键词传 null 即可
            return searchPosts(null, page, size, "hot");
        }

        // =================================================================================
        // 【原有逻辑】Mongo 处理场景 (最新、最旧、关注流、标签筛选)
        // =================================================================================

        // 2. 构建 Mongo 排序
        Sort mongoSort;
        if ("new".equalsIgnoreCase(sort)) {
            mongoSort = Sort.by(Sort.Direction.DESC, "createdAt");
        } else if ("old".equalsIgnoreCase(sort)) {
            mongoSort = Sort.by(Sort.Direction.ASC, "createdAt");
        } else {
            // 兜底：虽然 hot 走了 ES，但如果用户非要在 "follow" tab 下按热度排，
            // 或者查特定 tag 按热度排，Mongo 只能简单按点赞倒序兜底
            mongoSort = Sort.by(Sort.Direction.DESC, "likeCount", "createdAt");
        }

        Pageable pageable = PageRequest.of(pageNum, pageSize, mongoSort);
        Page<PostDoc> postDocPage;

        // 场景 A: 标签筛选
        if (StrUtil.isNotBlank(tag)) {
            postDocPage = postRepository.findByTagsContainingAndIsDeleted(tag, 0, pageable);
        }
        // 场景 B: 关注流
        else if ("follow".equalsIgnoreCase(tab)) {
            Long currentUserId = UserContext.getUserId();
            if (currentUserId == null) throw new AppException(ResultCode.UNAUTHORIZED);

            List<UserFollowDoc> follows = userFollowRepository.findFollowingIds(currentUserId);
            if (CollUtil.isEmpty(follows)) {
                postDocPage = Page.empty(pageable);
            } else {
                List<Long> targetIds = follows.stream().map(UserFollowDoc::getTargetUserId).collect(Collectors.toList());
                postDocPage = postRepository.findByUserIdInAndStatusAndIsDeleted(targetIds, 1, 0, pageable);
            }
        }
        // 场景 C: 推荐流 (非热门的普通推荐，即按时间倒序)
        else {
            postDocPage = postRepository.findByStatusAndIsDeleted(1, 0, pageable);
        }

        return buildResultMap(postDocPage);
    }

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    @Override
    public Map<String, Object> searchPosts(String keyword, Integer page, Integer size, String sort) {
        Long currentUserId = UserContext.getUserId();

        // 1. 记录搜索历史 (仅当有关键词时)
        if (currentUserId != null && StrUtil.isNotBlank(keyword)) {
            rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "search.history", new UserSearchEvent(currentUserId, keyword));
        }

        // 2. 参数处理
        int pageNum = (page == null || page < 1) ? 0 : page - 1;
        int pageSize = (size == null || size < 1) ? 20 : size;

        // 3. 构建 ES 查询 Builder
        NativeQueryBuilder queryBuilder = NativeQuery.builder();

        // 3.1 构造基础 Query (有词搜词，没词搜全部)
        // 如果 keyword 为空，说明是 getPostList 转过来的，要查所有帖子
        Query baseQuery;
        if (StrUtil.isNotBlank(keyword)) {
            baseQuery = QueryBuilders.multiMatch()
                    .query(keyword)
                    .fields("title^3", "title.pinyin^1.5", "content", "tags^2")
                    .build()._toQuery();
        } else {
            baseQuery = QueryBuilders.matchAll().build()._toQuery();
        }

        // 3.2 排序策略
        if ("new".equalsIgnoreCase(sort)) {
            queryBuilder.withQuery(baseQuery);
            queryBuilder.withSort(Sort.by(Sort.Direction.DESC, "createdAt"));
        }
        else if ("old".equalsIgnoreCase(sort)) {
            queryBuilder.withQuery(baseQuery);
            queryBuilder.withSort(Sort.by(Sort.Direction.ASC, "createdAt"));
        }
        else if ("likes".equalsIgnoreCase(sort)) {
            queryBuilder.withQuery(baseQuery);
            queryBuilder.withSort(Sort.by(Sort.Direction.DESC, "likeCount"));
        }
        else {
            // =============================================================
            // [F] 综合热度 (Hot/Default) - 使用 Function Score
            // 核心公式：最终分 = (BM25相关度) * (点赞加成) * (时间衰减)
            // =============================================================
            queryBuilder.withQuery(q -> q.functionScore(fs -> fs
                    .query(baseQuery) // 基础查询
                    .functions(f -> f
                            // 函数1：点赞数加分 (使用 log1p 平滑：log(likeCount + 1))
                            .filter(QueryBuilders.matchAll().build()._toQuery()) // 对所有文档生效
                            .fieldValueFactor(fv -> fv
                                    .field("likeCount")
                                    .modifier(FieldValueFactorModifier.Log1p) // 防止点赞数超级大导致分数失衡
                                    .factor(1.0)
                                    .missing(0.0) // 没点赞的默认为0
                            )
                    )
                    .functions(f -> f
                            // 函数2：高斯衰减 (Gauss Decay)
                            .filter(QueryBuilders.matchAll().build()._toQuery())
                            .gauss(g -> g
                                    .field("createdAt") // 针对创建时间字段
                                    .placement(p -> p
                                            .origin(JsonData.of(java.time.LocalDateTime.now()
                                                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS"))))   // 原点：当前时间
                                            .scale(JsonData.of("3d"))
                                            .offset(JsonData.of("1d"))
                                            .decay(0.5)         // 衰减率：到scale时衰减到0.5
                                    )
                            )
                    )
                    .boostMode(FunctionBoostMode.Multiply) // 乘法模式
            ));

            // 注意：Function Score 自动按 _score 排序，不需要额外 setSort
            // 如果想强制保证 _score 一样时按时间排，可以加第二排序键
            queryBuilder.withSort(Sort.by(Sort.Direction.DESC, "_score"));
            queryBuilder.withSort(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        // 4. 分页与过滤
        queryBuilder.withPageable(PageRequest.of(pageNum, pageSize));
        // 不查 content 字段，减少网络传输 IO
        queryBuilder.withSourceFilter(new FetchSourceFilter(
                new String[]{"id", "title", "content", "cover", "coverWidth", "coverHeight", "type", "tags", "userId", "userNickname", "userAvatar", "likeCount", "createdAt"},
                null
        ));
        SearchHits<PostEsDoc> searchHits = null;
        // 5. 执行搜索
        try {
           searchHits = esOperations.search(queryBuilder.build(), PostEsDoc.class);
        } catch (UncategorizedElasticsearchException e) {
            // 打印具体的响应体，这里面会告诉你是哪个字段、什么原因解析失败
            log.error("ES 详细报错原因: {}", e.getResponseBody());
            throw e;
        }

        // =======================================================
        // 6. 批量查询交互状态 (保持原逻辑不变)
        // =======================================================
        Set<String> likedPostIds = new HashSet<>();
        if (currentUserId != null && searchHits.hasSearchHits()) {
            List<String> postIds = searchHits.getSearchHits().stream()
                    .map(hit -> hit.getContent().getId())
                    .collect(Collectors.toList());
            // 这一步去查 MySQL/Mongo 确认用户点赞状态
            List<PostLikeDoc> likes = postLikeRepository.findByUserIdAndPostIdIn(currentUserId, postIds);
            likedPostIds = likes.stream().map(PostLikeDoc::getPostId).collect(Collectors.toSet());
        }

        // 7. 组装结果
        Set<String> finalLikedPostIds = likedPostIds;
        List<Map<String, Object>> resultList = searchHits.getSearchHits().stream().map(hit -> {
            PostEsDoc doc = hit.getContent();
            Map<String, Object> map = new HashMap<>();

            map.put("id", doc.getId());
            map.put("title", doc.getTitle());
            map.put("content", StrUtil.subPre(doc.getContent(), 50));
            map.put("cover", doc.getCover());
            map.put("coverWidth", doc.getCoverWidth());
            map.put("coverHeight", doc.getCoverHeight());
            map.put("type", doc.getType());
            map.put("tags", doc.getTags());
            map.put("likeCount", doc.getLikeCount());
            map.put("createdAt", formatter.format(doc.getCreatedAt()));
            // 作者信息
            Map<String, Object> author = new HashMap<>();
            author.put("userId", doc.getUserId() != null ? doc.getUserId().toString() : "");
            author.put("nickname", doc.getUserNickname());
            author.put("avatar", doc.getUserAvatar());
            map.put("author", author);

            // 状态
            map.put("isLiked", finalLikedPostIds.contains(doc.getId()));

            return map;
        }).collect(Collectors.toList());

        // 8. 返回
        Map<String, Object> response = new HashMap<>();
        response.put("records", resultList);
        response.put("total", searchHits.getTotalHits());
        response.put("current", pageNum + 1);
        response.put("size", pageSize);

        return response;
    }

    @Override
    public List<String> getSearchSuggestions(String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return new ArrayList<>();
        }

        // 1. 构建查询 `
        NativeQueryBuilder queryBuilder = NativeQuery.builder();
        BoolQuery.Builder boolQueryBuilder = QueryBuilders.bool();

        // 【核心修改】判断 keyword 是否包含中文
        // 正则表达式：[\u4e00-\u9fa5] 匹配常见汉字
        boolean hasChinese = keyword.matches(".*[\\u4e00-\\u9fa5].*");

        if (hasChinese) {
            // ==========================================
            // 场景 A: 输入包含中文 (如 "大学", "深圳")
            // 策略: 只查 IK 分词字段，不查拼音！防止 "d,x" 匹配到 "年度杏子"
            // ==========================================
            boolQueryBuilder
                    .should(s -> s.matchPhrasePrefix(m -> m
                            .field("title") // 查原标题 (IK)
                            .query(keyword)
                    ))
                    .should(s -> s.matchPhrasePrefix(m -> m
                            .field("tags")  // 查原标签 (IK)
                            .query(keyword)
                    ));
        } else {
            // ==========================================
            // 场景 B: 纯英文/拼音 (如 "shen", "sz", "shenzhen")
            // 策略: 查 Pinyin 字段
            // ==========================================
            boolQueryBuilder
                    .should(s -> s.matchPhrasePrefix(m -> m
                            .field("title.pinyin")
                            .query(keyword)
                            .slop(2)
                    ))
                    .should(s -> s.matchPhrasePrefix(m -> m
                            .field("tags.pinyin")
                            .query(keyword)
                    ));
        }
        queryBuilder.withQuery(boolQueryBuilder.build()._toQuery());

        // 2. 设置分页
        queryBuilder.withPageable(PageRequest.of(0, 10));

        // 3. 设置高亮
        HighlightParameters parameters = HighlightParameters.builder()
                .withPreTags("<em>")
                .withPostTags("</em>")
                .withRequireFieldMatch(false)
                .withNumberOfFragments(1)
                .withFragmentSize(50)
                .build();

        List<HighlightField> fields = List.of(
                new HighlightField("title"),       // 对应中文匹配的高亮
                new HighlightField("tags"),
                new HighlightField("title.pinyin"), // 对应拼音匹配的高亮
                new HighlightField("tags.pinyin")
        );

        // Spring Data ES 5.x 要求 parameters 在前，fields 在后
        queryBuilder.withHighlightQuery(new HighlightQuery(
                new Highlight(parameters, fields),
                PostEsDoc.class
        ));

        queryBuilder.withSourceFilter(new FetchSourceFilter(
                new String[]{"title", "tags"}, null
        ));

        // 4. 执行搜索
        SearchHits<PostEsDoc> searchHits = esOperations.search(queryBuilder.build(), PostEsDoc.class);

        // 5. 解析结果
        // 使用 LinkedHashSet 保证插入顺序：先插的在前面
        Set<String> suggestions = new LinkedHashSet<>();

        suggestions.add("<em>" + keyword + "/<em>");

        for (org.springframework.data.elasticsearch.core.SearchHit<PostEsDoc> hit : searchHits.getSearchHits()) {
            // A. 尝试获取标题高亮
            List<String> hlTitle = hit.getHighlightField("title");
            List<String> hlTitlePy = hit.getHighlightField("title.pinyin");

            if (CollUtil.isNotEmpty(hlTitle)) {
                suggestions.add(hlTitle.get(0));
            } else if (CollUtil.isNotEmpty(hlTitlePy)) {
                suggestions.add(hlTitlePy.get(0));
            }

            // B. 尝试获取标签高亮
            List<String> hlTags = hit.getHighlightField("tags");
            List<String> hlTagsPy = hit.getHighlightField("tags.pinyin");

            if (CollUtil.isNotEmpty(hlTags)) {
                suggestions.add(hlTags.get(0));
            } else if (CollUtil.isNotEmpty(hlTagsPy)) {
                suggestions.add(hlTagsPy.get(0));
            }
        }

        // 6. 返回结果
        // 限制总数 (包含 keyword 在内最多返回 10 条)
        return suggestions.stream().limit(10).collect(Collectors.toList());
    }

    @Override
    public List<String> getSearchHistory(Long userId) {
        // 按时间倒序，取前 10 条
        Pageable pageable = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<SearchHistoryDoc> page = searchHistoryRepository.findByUserId(userId, pageable);

        // 提取关键词
        return page.getContent().stream()
                .map(SearchHistoryDoc::getKeyword)
                .collect(Collectors.toList());
    }

    @Override
    public void clearSearchHistory(Long userId) {
        searchHistoryRepository.deleteByUserId(userId);
    }

    // 实现删除单条历史
    @Override
    public void deleteSearchHistoryItem(Long userId, String keyword) {
        // 直接调用 Repository 的衍生查询方法即可
        // 这里的操作是原子的，且 SearchHistoryDoc 没有冗余的用户昵称/头像，
        // 所以不需要发布类似 UserUpdateEvent 的事件来维护一致性。
        if (userId != null && StrUtil.isNotBlank(keyword)) {
            searchHistoryRepository.deleteByUserIdAndKeyword(userId, keyword);
        }
    }

    @Override
    public PostVO getPostDetail(String postId) {
        PostDoc doc = postRepository.findById(postId).orElse(null);

        // 1. 基础判空
        if (doc == null || (doc.getIsDeleted() != null && doc.getIsDeleted() == 1)) {
            throw new AppException(ResultCode.RESOURCE_NOT_FOUND);
        }

        // 2. 权限校验：如果不是发布状态
        if (doc.getStatus() != null && doc.getStatus() != 1) {
            // 获取当前登录用户
            Long currentUserId = UserContext.getUserId();
            // 如果当前没人登录，或者登录的人不是作者，则抛出异常
            // 意味着：作者本人可以看自己 审核中(0) 或 审核失败(2) 的帖子
            if (currentUserId == null || !currentUserId.equals(doc.getUserId())) {
                throw new AppException(ResultCode.RESOURCE_NOT_FOUND);
            }
        }

        // 记录浏览历史 (只有登录用户才记录)
        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null) {
            // 调用刚才在 UserService 写的异步方法
            userService.recordBrowsingHistory(currentUserId, postId);
        }

        return convertToVO(doc, true);
    }


    /**
     * 获取热门标签 (Redis缓存 + Mongo聚合)
     * 策略：
     * 1. 先查 Redis，有则直接返回
     * 2. 无缓存则查 Mongo 聚合：筛选->拆分->分组计数->排序->截取
     * 3. 结果混入默认标签兜底
     * 4. 写入 Redis (有效期30分钟)
     */
    @Override
    public List<String> getHotTags(int limit) {
        // 定义 Redis Key
        String cacheKey = "rednote:tags:hot";

        // =================================================
        // 1. 【缓存层】尝试从 Redis 读取
        // =================================================
        String jsonStr = redisTemplate.opsForValue().get(cacheKey);
        if (StrUtil.isNotBlank(jsonStr)) {
            // 命中缓存，直接反序列化返回
            return JSONUtil.toList(jsonStr, String.class);
        }

        // =================================================
        // 2. 【数据层】缓存未命中，执行 MongoDB 聚合统计
        // =================================================

        // 构建聚合管道
        Aggregation aggregation = Aggregation.newAggregation(
                // A. 筛选：只统计状态正常(已发布且未删除)的帖子
                Aggregation.match(Criteria.where("isDeleted").is(0).and("status").is(1)),

                // B. 拆分(Unwind)：将 tags 数组拆解为单条记录
                // { tags: ["A", "B"] } -> { tags: "A" }, { tags: "B" }
                Aggregation.unwind("tags"),

                // C. 分组(Group)：按 tags 字段分组，统计出现次数
                Aggregation.group("tags").count().as("count"),

                // D. 排序(Sort)：按 count 倒序
                Aggregation.sort(Sort.Direction.DESC, "count"),

                // E. 截取(Limit)：取前 N 个 (稍微多取一点，防止后面有空字符串被过滤掉)
                Aggregation.limit(limit + 5),

                // F. 投影(Project)：只保留 _id (即标签名)
                Aggregation.project("_id")
        );

        // 执行查询
        // 结果映射为 Map，其中 _id 是标签名
        AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, PostDoc.class, Map.class);

        // =================================================
        // 3. 【业务层】结果处理与兜底
        // =================================================
        List<String> hotTags = new ArrayList<>();

        // 3.1 固定首位："推荐" (前端通常需要这个作为默认 Tab)
        hotTags.add("推荐");

        // 3.2 填充聚合结果
        for (Map row : results.getMappedResults()) {
            String tag = (String) row.get("_id");
            // 简单清洗：非空且不等于"推荐"（防止重复）
            if (StrUtil.isNotBlank(tag) && !"推荐".equals(tag)) {
                hotTags.add(tag);
            }
            // 够数就停
            if (hotTags.size() >= limit + 1) break;
        }

        // 3.3 兜底逻辑：如果数据库没帖子，或者聚合出来的标签太少，用默认词填充
        // 保证首页 Tab 栏看起来是满的
        if (hotTags.size() < limit) {
            List<String> defaults = Arrays.asList(
                    "美食", "穿搭", "彩妆", "影视", "职场",
                    "情感", "家居", "游戏", "旅行", "健身", "科技", "学习"
            );
            for (String def : defaults) {
                if (!hotTags.contains(def)) {
                    hotTags.add(def);
                }
                if (hotTags.size() >= limit + 1) break;
            }
        }

        // =================================================
        // 4. 【缓存回写】存入 Redis
        // =================================================
        // 热门标签不需要实时更新，10分钟更新一次足够了
        redisTemplate.opsForValue().set(cacheKey, JSONUtil.toJsonStr(hotTags), 10, TimeUnit.SECONDS);

        return hotTags;
    }

    /**
     * 获取用户的帖子列表
     */
    @Override
    public Map<String, Object> getUserPostList(String userIdStr, Integer page, Integer size) {
        // 1. 参数校验
        long targetUserId;
        try {
            targetUserId = Long.parseLong(userIdStr);
        } catch (NumberFormatException e) {
            throw new AppException(ResultCode.PARAM_ERROR, "用户ID格式错误");
        }

        int pageNum = (page == null || page < 1) ? 0 : page - 1;
        int pageSize = (size == null || size < 1) ? 20 : size;

        Pageable pageable = PageRequest.of(pageNum, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        // 2. 权限判断
        Long currentUserId = UserContext.getUserId();
        Page<PostDoc> postDocPage;

        // 【修改逻辑】：判断是否是“我看自己”
        if (currentUserId != null && currentUserId.equals(targetUserId)) {
            // A. 看自己：查询该用户所有“未逻辑删除”的帖子
            // 包含：Status=0 (审核中/草稿), Status=1 (已发布), Status=2 (被退回)
            postDocPage = postRepository.findByUserIdAndIsDeleted(targetUserId, 0, pageable);
        } else {
            // B. 看别人：只允许查询 Status=1 (已发布) 的帖子
            // 必须过滤掉审核中和审核失败的，否则会泄露隐私
            postDocPage = postRepository.findByUserIdAndStatusAndIsDeleted(targetUserId, 1, 0, pageable);
        }

        return buildResultMap(postDocPage);
    }

    // 阿里云 OSS 视频截帧参数: 截取第1000ms, 输出jpg, 模式为fast
    private static final String OSS_VIDEO_SNAPSHOT_PARAM = "?x-oss-process=video/snapshot,t_1000,f_jpg,w_0,h_0,m_fast";
    // 【新增】读取配置文件中的审核开关
    @Value("${app.post.audit-enable:false}")
    private boolean auditEnable;

    @Override
    public String createPost(PostCreateDTO dto) {
        // 1. 获取当前登录用户ID
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new AppException(ResultCode.UNAUTHORIZED);
        }

        // 2. 获取用户详细信息 (用于 MongoDB 冗余存储)
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new AppException(ResultCode.USER_NOT_FOUND);
        }

        // 3. 构建 MongoDB 文档对象
        PostDoc post = new PostDoc();
        post.setUserId(userId);

        // 冗余字段填充 (保证创建时的一致性)
        post.setUserNickname(user.getNickname());
        post.setUserAvatar(user.getAvatar());

        post.setTitle(dto.getTitle());
        post.setContent(dto.getContent());
        post.setTags(dto.getTags());

        // 4. 严格互斥逻辑
        post.setType(dto.getType());

        // --- 资源与封面处理 ---
        List<String> finalResources = new ArrayList<>();
        String finalCover = "";

        if (dto.getType() == 0 || dto.getType() == 2) {
            // 场景: 图文(0) / 纯文字(2) -> 必须传 images
            if (CollUtil.isEmpty(dto.getImages())) {
                throw new AppException(ResultCode.PARAM_ERROR, "图片不能为空");
            }
            finalResources.addAll(dto.getImages());
            // 封面默认取第1张
            finalCover = dto.getImages().get(0);

        } else if (dto.getType() == 1) {
            // 场景: 视频(1) -> 必须传 video (String)
            if (StrUtil.isBlank(dto.getVideo())) {
                throw new AppException(ResultCode.PARAM_ERROR, "视频不能为空");
            }
            finalResources.add(dto.getVideo());
            // 封面自动由 OSS 生成
            finalCover = dto.getVideo() + OSS_VIDEO_SNAPSHOT_PARAM;
        } else {
            throw new AppException(ResultCode.PARAM_ERROR, "未知的帖子类型");
        }

        post.setResources(finalResources);
        post.setCover(finalCover);
        post.setCoverWidth(dto.getCoverWidth());
        post.setCoverHeight(dto.getCoverHeight());
        // -----------------------------

        // 5. 初始化统计数据
        post.setViewCount(0);
        post.setLikeCount(0);
        post.setCollectCount(0);
        post.setCommentCount(0);
        post.setRatingAverage(0.0);
        post.setRatingCount(0);

        // 根据配置决定初始状态
        // auditEnable = true  -> status = 0 (审核中)
        // auditEnable = false -> status = 1 (直接发布)
        post.setStatus(auditEnable ? 0 : 1);
        post.setIsDeleted(0);

        post.setCreatedAt(java.time.LocalDateTime.now());
        post.setUpdatedAt(java.time.LocalDateTime.now());

        // 7. 保存到 MongoDB
        postRepository.save(post);


        Integer type =  post.getType();

        PostCreateEvent event = new PostCreateEvent(
                post.getId(),
                post.getContent(),
                post.getTitle(),
                type != 1 ? post.getResources() : null, // 如果是图片/文字
                type == 1 ? post.getResources().get(0) : null, // 如果是视频
                userId,
                post.getTags(),
                post.getType(),
                post.getCover(),
                post.getCoverWidth(),
                post.getCoverHeight(),
                user.getNickname(),
                user.getAvatar()
        );
        // 发送消息
        rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "post.create", event);
        return post.getId();
    }

    @Override
    public void deletePost(String postId) {
        // 1. 获取当前登录用户
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new AppException(ResultCode.UNAUTHORIZED);
        }

        // 2. 查询帖子是否存在
        PostDoc post = postRepository.findById(postId).orElse(null);
        if (post == null || (post.getIsDeleted() != null && post.getIsDeleted() == 1)) {
            throw new AppException(ResultCode.RESOURCE_NOT_FOUND, "帖子不存在或已删除");
        }

        // 3. 权限校验：必须是作者本人 OR 管理员
        if (!post.getUserId().equals(currentUserId)) {
            // 如果不是作者，查一下数据库看是不是管理员
            User user = userMapper.selectById(currentUserId);
            if (user == null || !"ADMIN".equalsIgnoreCase(user.getRole())) {
                throw new AppException(ResultCode.UNAUTHORIZED, "无权删除他人帖子");
            }
        }

        // 4. 执行逻辑删除 (Soft Delete)
        // 我们只标记帖子为删除，关联数据的清理交给 Listener 异步处理
        post.setIsDeleted(1);
        post.setStatus(2); // 可选：标记状态为审核失败或特定状态，防止被搜索出来
        post.setUpdatedAt(java.time.LocalDateTime.now());

        postRepository.save(post);

        // 5. 发布事件 (Spring Event)
        // 解耦：Service 只管改状态，后续的 Redis 清理、关联数据删除交给 Listener
        rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "post.delete",
                new PostDeleteEvent(postId, currentUserId));
    }

    @Override
    public void updatePost(String postId, PostUpdateDTO dto) {
        Long currentUserId = UserContext.getUserId();
        if (currentUserId == null) {
            throw new AppException(ResultCode.UNAUTHORIZED);
        }

        PostDoc post = postRepository.findById(postId).orElse(null);
        if (post == null || (post.getIsDeleted() != null && post.getIsDeleted() == 1)) {
            throw new AppException(ResultCode.RESOURCE_NOT_FOUND);
        }

        if (!post.getUserId().equals(currentUserId)) {
            throw new AppException(ResultCode.UNAUTHORIZED, "无权修改他人帖子");
        }

        boolean needAudit = false;

        // 1. 更新基本文本
        if (StrUtil.isNotBlank(dto.getTitle())) {
            post.setTitle(dto.getTitle());
            needAudit = true;
        }
        if (StrUtil.isNotBlank(dto.getContent())) {
            post.setContent(dto.getContent());
            needAudit = true;
        }
        if (dto.getTags() != null) {
            post.setTags(dto.getTags());
            needAudit = true;
        }

        // 2. 更新资源 (严格按照原有类型更新，不允许 Update 时修改 Type)
        Integer currentType = post.getType();
        boolean mediaChanged = false;

        if (currentType == 0 || currentType == 2) {
            // === 图文/文字贴 ===
            if (StrUtil.isNotBlank(dto.getVideo())) {
                throw new AppException(ResultCode.PARAM_ERROR, "图文帖子无法转为视频帖");
            }
            if (CollUtil.isNotEmpty(dto.getImages())) {
                // 覆盖旧资源
                post.setResources(dto.getImages());
                // 更新封面为第1张
                post.setCover(dto.getImages().get(0));
                post.setCoverHeight(dto.getCoverHeight());
                post.setCoverWidth(dto.getCoverWidth());
                mediaChanged = true;
            }
        } else if (currentType == 1) {
            // === 视频贴 ===
            if (CollUtil.isNotEmpty(dto.getImages())) {
                throw new AppException(ResultCode.PARAM_ERROR, "视频帖子无法转为图文帖");
            }
            if (StrUtil.isNotBlank(dto.getVideo())) {
                // 覆盖旧视频
                List<String> newRes = new ArrayList<>();
                newRes.add(dto.getVideo());
                post.setResources(newRes);
                // 更新封面 (OSS)
                post.setCover(dto.getVideo() + OSS_VIDEO_SNAPSHOT_PARAM);
                post.setCoverHeight(dto.getCoverHeight());
                post.setCoverWidth(dto.getCoverWidth());
                mediaChanged = true;
            }
        }
        if (mediaChanged) {
            needAudit = true;
        }

        // 3. 状态重置与保存
        if (needAudit) {
            // 如果打开审核开关，则设置为未审核状态
            post.setStatus(auditEnable ? 0 : 1);
        }
        post.setUpdatedAt(java.time.LocalDateTime.now());
        postRepository.save(post);

        // 5. 发布事件
        if (needAudit) {
            rabbitTemplate.convertAndSend(RabbitConfig.PLATFORM_EXCHANGE, "post.update", new PostUpdateEvent(post.getId(), post.getTitle(), post.getContent()));
        }
    }
    // --- Private Methods ---

    // 1. 修改 buildResultMap 方法
    private Map<String, Object> buildResultMap(Page<PostDoc> pageData) {
        List<PostDoc> postDocs = pageData.getContent();

        // --- 批量查询优化开始 ---

        // 准备三个 Set 用于 O(1) 快速查找
        Set<String> likedPostIds = new HashSet<>();
        Set<String> collectedPostIds = new HashSet<>();
        Set<Long> followedUserIds = new HashSet<>();

        Long currentUserId = UserContext.getUserId();

        // 只有登录用户才需要查这些状态
        if (currentUserId != null && CollUtil.isNotEmpty(postDocs)) {
            // A. 收集本页所有的 postId
            List<String> postIds = postDocs.stream().map(PostDoc::getId).collect(Collectors.toList());
            // B. 收集本页所有的 authorId (去重)
            List<Long> authorIds = postDocs.stream().map(PostDoc::getUserId).distinct().collect(Collectors.toList());

            // C. 批量查询点赞状态
            // 查出这批帖子中，我点赞过哪些
            List<PostLikeDoc> likes = postLikeRepository.findByUserIdAndPostIdIn(currentUserId, postIds);
            likedPostIds = likes.stream().map(PostLikeDoc::getPostId).collect(Collectors.toSet());

            // D. 批量查询收藏状态
            List<PostCollectDoc> collects = postCollectRepository.findByUserIdAndPostIdIn(currentUserId, postIds);
            collectedPostIds = collects.stream().map(PostCollectDoc::getPostId).collect(Collectors.toSet());

            // E. 批量查询关注状态
            List<UserFollowDoc> follows = userFollowRepository.findByUserIdAndTargetUserIdIn(currentUserId, authorIds);
            followedUserIds = follows.stream().map(UserFollowDoc::getTargetUserId).collect(Collectors.toSet());
        }

        // --- 批量查询优化结束 ---

        // 2. 转换 VO，传入刚才查好的 Set
        // 注意：这里需要配合修改下面的 convertToVO 方法，让它支持传入状态
        Set<String> finalLiked = likedPostIds;
        Set<String> finalCollected = collectedPostIds;
        Set<Long> finalFollowed = followedUserIds;

        List<PostVO> records = postDocs.stream()
                .map(doc -> convertToVO(doc, false, finalLiked, finalCollected, finalFollowed))
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", pageData.getTotalElements());
        result.put("current", pageData.getNumber() + 1);
        result.put("size", pageData.getSize());
        return result;
    }
    // 3. 重载 convertToVO 方法 (用于列表页，接受批量查询的结果)
    private PostVO convertToVO(PostDoc doc, boolean isDetail,
                               Set<String> likedSet,
                               Set<String> collectedSet,
                               Set<Long> followedSet) {
        // 先调用基础转换逻辑 (复用代码)
        PostVO vo = baseConvertToVO(doc, isDetail);

        // 直接从内存 Set 中判断，不再查库
        vo.setIsLiked(likedSet.contains(doc.getId()));
        vo.setIsCollected(collectedSet.contains(doc.getId()));
        vo.setIsFollowed(followedSet.contains(doc.getUserId()));

        return vo;
    }
    // 4. 原有的 convertToVO 方法 (用于详情页，单条查询)
    // 详情页本身就只查一次，不需要批量优化，保留原有逻辑即可，或者调用 base
    private PostVO convertToVO(PostDoc doc, boolean isDetail) {
        PostVO vo = baseConvertToVO(doc, isDetail);

        Long currentUserId = UserContext.getUserId();
        if (currentUserId != null) {
            vo.setIsLiked(postLikeRepository.existsByUserIdAndPostId(currentUserId, doc.getId()));
            vo.setIsCollected(postCollectRepository.existsByUserIdAndPostId(currentUserId, doc.getId()));
            vo.setIsFollowed(userFollowRepository.existsByUserIdAndTargetUserId(currentUserId, doc.getUserId()));
            // 【新增】 查询当前用户对该帖子的具体评分 (myScore)
            postRatingRepository.findByUserIdAndPostId(currentUserId, doc.getId())
                    .ifPresent(rating -> vo.setMyScore(rating.getScore()));
        } else {
            vo.setIsLiked(false);
            vo.setIsCollected(false);
            vo.setIsFollowed(false);
        }
        return vo;
    }

    // 5. 抽取公共的基础转换逻辑 (避免代码重复)
    private PostVO baseConvertToVO(PostDoc doc, boolean isDetail) {
        PostVO vo = new PostVO();
        vo.setId(doc.getId());

        UserInfo author = new UserInfo();
        author.setUserId(String.valueOf(doc.getUserId()));
        author.setNickname(doc.getUserNickname());
        author.setAvatar(doc.getUserAvatar());
        vo.setAuthor(author);

        vo.setTitle(doc.getTitle());
        // 列表页摘要
        if (isDetail) {
            vo.setContent(doc.getContent());
        } else {
            vo.setContent(StrUtil.subPre(doc.getContent(), 50));
        }

        vo.setTags(doc.getTags());
        vo.setType(doc.getType());
        vo.setCover(doc.getCover()); // 直接返回封面
        vo.setCoverWidth(doc.getCoverWidth());
        vo.setCoverHeight(doc.getCoverHeight());

        // 详情页才返回具体资源
        if (isDetail) {
            if (doc.getType() != null && doc.getType() == 1) {
                // 视频贴：填充 video 字段
                if (CollUtil.isNotEmpty(doc.getResources())) {
                    vo.setVideo(doc.getResources().get(0));
                }
                vo.setImages(Collections.emptyList());
            } else {
                // 图文贴：填充 images 字段
                vo.setImages(doc.getResources());
                vo.setVideo(null);
            }
        } else {
            // 列表页：资源置空，只留 cover 节省流量
            vo.setImages(Collections.emptyList());
            vo.setVideo(null);
        }

        vo.setLikeCount(doc.getLikeCount());
        vo.setCollectCount(doc.getCollectCount());
        vo.setCommentCount(doc.getCommentCount());

        vo.setRatingAverage(doc.getRatingAverage()); // 这里的 doc.getRatingAverage() 对应 PostDoc 的字段
        vo.setRatingCount(doc.getRatingCount());

        if (doc.getCreatedAt() != null) {
            vo.setCreatedAt(doc.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        }

        return vo;
    }
}