package com.company.bds.listing.application.service;

import java.util.ArrayList;
import java.util.List;

/** Minimal RFC 4180 reader (quoted fields, doubled quotes, CRLF/LF, embedded newlines). Also accepts ';' separators. */
final class CsvReader {
    private CsvReader() {}

    record Line(int number, List<String> cells) {}

    static List<Line> parse(String text) {
        if (text.startsWith("﻿")) text = text.substring(1);
        char separator = detectSeparator(text);
        List<Line> lines = new ArrayList<>();
        List<String> cells = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        int lineNumber = 1;
        int startLine = 1;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                    else quoted = false;
                } else {
                    if (c == '\n') lineNumber++;
                    cell.append(c);
                }
            } else if (c == '"' && cell.isEmpty()) {
                quoted = true;
            } else if (c == separator) {
                cells.add(cell.toString());
                cell.setLength(0);
            } else if (c == '\r') {
                // ignored; the following \n ends the line
            } else if (c == '\n') {
                cells.add(cell.toString());
                cell.setLength(0);
                add(lines, startLine, cells);
                cells = new ArrayList<>();
                lineNumber++;
                startLine = lineNumber;
            } else {
                cell.append(c);
            }
        }
        if (quoted) throw new IllegalArgumentException("Tệp CSV có dấu ngoặc kép chưa đóng (dòng " + startLine + ").");
        if (!cell.isEmpty() || !cells.isEmpty()) {
            cells.add(cell.toString());
            add(lines, startLine, cells);
        }
        return lines;
    }

    private static void add(List<Line> lines, int number, List<String> cells) {
        boolean blank = cells.stream().allMatch(value -> value.isBlank());
        if (!blank) lines.add(new Line(number, cells));
    }

    private static char detectSeparator(String text) {
        int end = text.indexOf('\n');
        String header = end < 0 ? text : text.substring(0, end);
        return header.chars().filter(ch -> ch == ';').count() > header.chars().filter(ch -> ch == ',').count() ? ';' : ',';
    }
}
