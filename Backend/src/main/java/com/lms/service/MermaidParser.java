package com.lms.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MermaidParser {

    private static final Pattern MERMAID_PATTERN = Pattern.compile("(graph (?:TD|TB|BT|RL|LR).*?)(?:```|$)", Pattern.DOTALL);

    public static String extractGraph(String aiResponse) {
        if (aiResponse == null) return "graph TD\n  A[No context found]";
        
        Matcher matcher = MERMAID_PATTERN.matcher(aiResponse);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "graph TD\n  A[Failed to generate valid mindmap]";
    }
}
