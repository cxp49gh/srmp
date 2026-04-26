package com.smartroad.srmp.dashboard.service;

import java.util.Map;

public interface DashboardService {

    Map<String, Object> overview(String routeCode, Integer year);

    Map<String, Object> diseaseSummary(String routeCode);

    Map<String, Object> assessmentSummary(String routeCode, Integer year);
}
