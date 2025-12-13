package com.szu.afternoon3.platform.service;

import com.szu.afternoon3.platform.entity.mongo.PostDoc;
import com.szu.afternoon3.platform.vo.AiAuditResultVO;

import java.util.List;

public interface AiService {
    List<String> generateTags(String title, String content,  List<String>  images, String video);
    String generatePostSummary(String title, String content,  List<String> images, String video);
    String generateInteractiveReply(String postTitle, String postContent,  List<String>  postImages, String postVideo, String parentContent, String userPrompt);
    AiAuditResultVO auditPostContent(PostDoc post);
}
