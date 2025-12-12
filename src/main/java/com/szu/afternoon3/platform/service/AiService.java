package com.szu.afternoon3.platform.service;

import java.util.List;

public interface AiService {
    String generatePostSummary(String title,String content);
    List<String> generateTags(String title, String content);
    String generateInteractiveReply(String postTitle, String postContent, String parentContent, String userPrompt);
}
