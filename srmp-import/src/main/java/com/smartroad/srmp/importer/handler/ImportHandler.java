package com.smartroad.srmp.importer.handler;

import java.util.Map;

public interface ImportHandler {
    String dataType();
    String handle(Map<String, String> row);
}