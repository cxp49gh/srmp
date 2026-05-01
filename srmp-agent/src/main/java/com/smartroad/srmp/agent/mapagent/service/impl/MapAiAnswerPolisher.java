package com.smartroad.srmp.agent.mapagent.service.impl;

import org.springframework.stereotype.Component;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Phase37.4.5：AI 回答清理器安全修复。
 *
 * 修复 Phase37.4.4 中 removeDuplicatedReferenceBlock 过于激进的问题：
 *
 * 原逻辑会匹配：
 *   ### 参考资料
 *   [一直到回答结尾]
 *
 * 但一张图 AI 的原始回答结构是：
 *   地图上下文
 *   参考资料
 *   建议
 *   当前对象专项处置建议 / 当前评定单元专项分析
 *
 * 所以旧逻辑会把“参考资料”之后的建议和专项分析全部删掉，导致 AI 回答只剩地图上下文。
 *
 * 本版原则：
 * 1. 不再删除“参考资料”段，避免误伤正文；
 * 2. 只移除明确的泛化建议句；
 * 3. 只做标题友好化和空行清理；
 * 4. 保障病害专项建议、评定单元专项分析不被删除。
 */
@Component
public class MapAiAnswerPolisher {

    private static final String GENERIC_ADVICE =
            "建议结合当前对象或区域的病害、评定结果和知识库资料开展现场复核，并根据严重程度、指标分值和周边关联对象确定处置优先级。";

    public String polish(String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            return answer;
        }

        String value = answer.trim();
        boolean hasSpecialAnalysis = value.contains("当前评定单元专项分析")
                || value.contains("当前对象专项处置建议");

        if (hasSpecialAnalysis) {
            value = removeGenericAdvice(value);
        }

        value = normalizeHeadings(value);
        value = cleanupBlankLines(value);
        return value;
    }

    private String removeGenericAdvice(String value) {
        String result = value;
        result = result.replace("### 建议\n" + GENERIC_ADVICE + "\n\n", "");
        result = result.replace("### 建议\r\n" + GENERIC_ADVICE + "\r\n\r\n", "");
        result = result.replace("建议\n\n" + GENERIC_ADVICE + "\n\n", "");
        result = result.replace("建议\r\n\r\n" + GENERIC_ADVICE + "\r\n\r\n", "");
        result = result.replace(GENERIC_ADVICE + "\n\n", "");
        result = result.replace(GENERIC_ADVICE + "\r\n\r\n", "");
        result = result.replace(GENERIC_ADVICE, "");
        return result;
    }

    private String normalizeHeadings(String value) {
        String result = value;
        result = result.replace("#### 1. 主要问题", "一、主要问题");
        result = result.replace("#### 2. 成因判断", "二、成因判断");
        result = result.replace("#### 3. 现场复核重点", "三、现场复核重点");
        result = result.replace("#### 4. 养护处置建议", "四、养护处置建议");
        result = result.replace("#### 5. 周边/同类评定结果参考", "五、周边/同类评定结果参考");
        result = result.replace("#### 4. 周边关联判断", "四、周边关联判断");
        result = result.replace("#### 5. 周边关联判断", "五、周边关联判断");
        result = result.replace("#### 参考依据", "参考依据");

        Pattern p = Pattern.compile("####\\s*(\\d+)\\.\\s*([^\\n\\r]+)");
        Matcher m = p.matcher(result);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String title = toChineseNumber(m.group(1)) + "、" + m.group(2).trim();
            m.appendReplacement(sb, Matcher.quoteReplacement(title));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String cleanupBlankLines(String value) {
        return value
                .replaceAll("[ \\t]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private String toChineseNumber(String num) {
        if ("1".equals(num)) return "一";
        if ("2".equals(num)) return "二";
        if ("3".equals(num)) return "三";
        if ("4".equals(num)) return "四";
        if ("5".equals(num)) return "五";
        if ("6".equals(num)) return "六";
        if ("7".equals(num)) return "七";
        if ("8".equals(num)) return "八";
        if ("9".equals(num)) return "九";
        return num;
    }
}
