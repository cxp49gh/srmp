package com.smartroad.srmp.agent.eval.controller;

import com.smartroad.srmp.agent.eval.dto.RagEvalCase;
import com.smartroad.srmp.agent.eval.dto.RagEvalRequest;
import com.smartroad.srmp.agent.eval.service.RagEvalService;
import com.smartroad.srmp.agent.eval.vo.RagEvalResponse;
import com.smartroad.srmp.common.core.R;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.*;

@RestController
@RequestMapping("/api/ai/eval/rag")
public class RagEvalController {

    @Resource
    private RagEvalService ragEvalService;

    @PostMapping("/run")
    public R<RagEvalResponse> run(@RequestBody RagEvalRequest request) {
        return R.ok(ragEvalService.run(request));
    }

    @GetMapping("/default-cases")
    public R<List<RagEvalCase>> defaultCases() {
        return R.ok(defaultCaseList());
    }

    private List<RagEvalCase> defaultCaseList() {
        List<RagEvalCase> list = new ArrayList<>();
        list.add(caseOf("disease-repair", "修补损坏怎么处理？", "修补损坏", Arrays.asList("复核", "基层", "排水", "局部"), Arrays.asList("修补损坏")));
        list.add(caseOf("crack", "中度裂缝如何处置？", "裂缝", Arrays.asList("灌缝", "封层", "渗水"), Arrays.asList("裂缝")));
        list.add(caseOf("mqi-pci", "MQI、PQI、PCI 分别是什么意思？", "评定指标", Arrays.asList("MQI", "PQI", "PCI"), Arrays.asList("指标")));
        return list;
    }

    private RagEvalCase caseOf(String id, String question, String diseaseName, List<String> keywords, List<String> sources) {
        RagEvalCase c = new RagEvalCase();
        c.setId(id);
        c.setQuestion(question);
        Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("objectType", "DISEASE");
        obj.put("routeCode", "G210");
        obj.put("diseaseName", diseaseName);
        obj.put("severity", "MEDIUM");
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("mode", "OBJECT");
        ctx.put("routeCode", "G210");
        ctx.put("year", 2026);
        ctx.put("mapObject", obj);
        c.setMapContext(ctx);
        c.setExpectedKeywords(keywords);
        c.setExpectedSources(sources);
        return c;
    }
}
