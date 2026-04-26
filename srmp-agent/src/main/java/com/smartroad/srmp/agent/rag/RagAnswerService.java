package com.smartroad.srmp.agent.rag;

import com.smartroad.srmp.agent.knowledge.dto.KnowledgeSearchRequest;
import com.smartroad.srmp.agent.knowledge.service.KnowledgeService;
import com.smartroad.srmp.agent.knowledge.vo.KnowledgeSearchResult;
import com.smartroad.srmp.agent.llm.LlmClient;
import com.smartroad.srmp.agent.outline.service.OutlineService;
import com.smartroad.srmp.agent.outline.vo.OutlineSearchResult;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;

@Service
public class RagAnswerService {

    @Resource
    private KnowledgeService knowledgeService;

    @Resource
    private OutlineService outlineService;

    @Resource
    private LlmClient llmClient;

    public Map answer(String question, String businessAnalysis, RagOptions options) {
        Map<String, Object> result = new LinkedHashMap<>();

        List<KnowledgeSearchResult> knowledgeSources = new ArrayList<>();
        List<OutlineSearchResult> outlineSources = new ArrayList<>();

        if (options.isUseKnowledge()) {
            KnowledgeSearchRequest searchRequest = new KnowledgeSearchRequest();
            searchRequest.setQuery(question);
            searchRequest.setTopK(options.getTopK());
            knowledgeSources = knowledgeService.search(searchRequest);
        }

        if (options.isUseOutline()) {
            outlineSources = outlineService.search(question, options.getTopK());
        }

        String prompt = buildPrompt(question, businessAnalysis, knowledgeSources, outlineSources);
        String answer = llmClient.chat("你是智路养护平台 AI 助手，请综合业务数据和知识库资料回答。资料不足时必须说明。", prompt);

        if (answer == null || answer.trim().isEmpty()) {
            answer = localAnswer(businessAnalysis, knowledgeSources, outlineSources);
        }

        result.put("answer", answer);
        result.put("knowledgeSources", knowledgeSources);
        result.put("outlineSources", outlineSources);
        result.put("usedKnowledge", options.isUseKnowledge());
        result.put("usedOutline", options.isUseOutline());
        return result;
    }

    private String buildPrompt(String question,
                               String businessAnalysis,
                               List<KnowledgeSearchResult> knowledgeSources,
                               List<OutlineSearchResult> outlineSources) {
        StringBuilder sb = new StringBuilder();
        sb.append("请基于以下资料回答用户问题。\n\n");

        if (businessAnalysis != null && businessAnalysis.trim().length() > 0) {
            sb.append("【业务数据分析】\n").append(businessAnalysis).append("\n\n");
        }

        sb.append("【本地知识库片段】\n");
        int i = 1;
        for (KnowledgeSearchResult item : knowledgeSources) {
            sb.append("片段").append(i++).append("\n");
            sb.append("标题：").append(item.getTitle()).append("\n");
            sb.append("来源：").append(item.getSourceType()).append("\n");
            if (item.getSourceUrl() != null) {
                sb.append("链接：").append(item.getSourceUrl()).append("\n");
            }
            sb.append("内容：").append(item.getContent()).append("\n\n");
        }

        sb.append("【Outline 文档片段】\n");
        i = 1;
        for (OutlineSearchResult item : outlineSources) {
            sb.append("片段").append(i++).append("\n");
            sb.append("标题：").append(item.getTitle()).append("\n");
            if (item.getUrl() != null) {
                sb.append("链接：").append(item.getUrl()).append("\n");
            }
            sb.append("内容：").append(item.getText()).append("\n\n");
        }

        sb.append("【回答要求】\n");
        sb.append("1. 优先引用业务数据；\n");
        sb.append("2. 知识库和 Outline 资料只能作为依据，不能编造；\n");
        sb.append("3. 如果资料不足，请明确说明；\n");
        sb.append("4. 涉及标准规范、流程、操作说明时，需要说明来源标题；\n");
        sb.append("5. 不直接生成正式决策，只生成建议或草稿。\n\n");

        sb.append("【用户问题】\n").append(question);
        return sb.toString();
    }

    private String localAnswer(String businessAnalysis,
                               List<KnowledgeSearchResult> knowledgeSources,
                               List<OutlineSearchResult> outlineSources) {
        StringBuilder sb = new StringBuilder();
        if (businessAnalysis != null && businessAnalysis.trim().length() > 0) {
            sb.append(businessAnalysis).append("\n\n");
        }
        if (!knowledgeSources.isEmpty()) {
            sb.append("本地知识库检索到以下相关资料：\n");
            for (int i = 0; i < knowledgeSources.size(); i++) {
                KnowledgeSearchResult item = knowledgeSources.get(i);
                sb.append(i + 1).append(". ").append(item.getTitle()).append("：")
                        .append(shortText(item.getContent(), 160)).append("\n");
            }
        }
        if (!outlineSources.isEmpty()) {
            sb.append("\nOutline 检索到以下相关资料：\n");
            for (int i = 0; i < outlineSources.size(); i++) {
                OutlineSearchResult item = outlineSources.get(i);
                sb.append(i + 1).append(". ").append(item.getTitle()).append("：")
                        .append(shortText(item.getText(), 160)).append("\n");
            }
        }
        if (sb.length() == 0) {
            return "当前未检索到足够的业务数据或知识库资料，无法给出可靠回答。";
        }
        return sb.toString();
    }

    private String shortText(String text, int max) {
        if (text == null) {
            return "";
        }
        return text.length() <= max ? text : text.substring(0, max) + "...";
    }
}
