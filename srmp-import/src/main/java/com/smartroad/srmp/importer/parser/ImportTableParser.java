package com.smartroad.srmp.importer.parser;

import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Component
public class ImportTableParser {

    public List<Map<String, String>> parse(MultipartFile file) throws IOException {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (name.endsWith(".xlsx") || name.endsWith(".xls")) {
            return parseExcel(file.getInputStream());
        }
        return parseCsv(file.getInputStream());
    }

    private List<Map<String, String>> parseExcel(InputStream inputStream) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (Workbook workbook = WorkbookFactory.create(inputStream)) {
            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null || sheet.getLastRowNum() < 1) return rows;
            Row header = sheet.getRow(0);
            List<String> headers = readHeader(header);
            DataFormatter formatter = new DataFormatter();
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) continue;
                Map<String, String> map = new LinkedHashMap<>();
                boolean allBlank = true;
                for (int c = 0; c < headers.size(); c++) {
                    String value = formatter.formatCellValue(row.getCell(c));
                    if (value != null && value.trim().length() > 0) allBlank = false;
                    map.put(headers.get(c), trim(value));
                }
                if (!allBlank) rows.add(map);
            }
        }
        return rows;
    }

    private List<String> readHeader(Row row) {
        List<String> headers = new ArrayList<>();
        if (row == null) return headers;
        DataFormatter formatter = new DataFormatter();
        for (int i = 0; i < row.getLastCellNum(); i++) {
            String h = trim(formatter.formatCellValue(row.getCell(i)));
            if (h != null && h.length() > 0) headers.add(h);
        }
        return headers;
    }

    private List<Map<String, String>> parseCsv(InputStream inputStream) throws IOException {
        List<Map<String, String>> rows = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String first = reader.readLine();
            if (first == null) return rows;
            first = removeBom(first);
            List<String> headers = parseCsvLine(first);
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                List<String> values = parseCsvLine(line);
                Map<String, String> map = new LinkedHashMap<>();
                for (int i = 0; i < headers.size(); i++) {
                    map.put(headers.get(i), i < values.size() ? trim(values.get(i)) : null);
                }
                rows.add(map);
            }
        }
        return rows;
    }

    /**
     * 简单 CSV 解析，支持英文逗号和双引号转义，足够覆盖模板类导入。
     */
    private List<String> parseCsvLine(String line) {
        List<String> result = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean quote = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quote && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    sb.append('"');
                    i++;
                } else {
                    quote = !quote;
                }
            } else if (ch == ',' && !quote) {
                result.add(trim(sb.toString()));
                sb.setLength(0);
            } else {
                sb.append(ch);
            }
        }
        result.add(trim(sb.toString()));
        return result;
    }

    private String trim(String s) {
        return s == null ? null : s.trim();
    }

    private String removeBom(String s) {
        if (s != null && s.startsWith("﻿")) return s.substring(1);
        return s;
    }
}