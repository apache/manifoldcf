package com.mcplusa.manifoldcf.agents.output.appsearch;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.manifoldcf.core.interfaces.ManifoldCFException;
import org.apache.manifoldcf.core.util.URLEncoder;

public final class Utils {

    public static final int APPSEARCH_MAX_ID_LENGTH = 780;

    private Utils() {
        // empty constructor
    }

    public static String cleanFieldName(String fieldName) {
        String regexWhitespaces = "\\s+";
        String regexSpecialCharacters = "\\W";
        return fieldName.replaceAll(regexWhitespaces, "_").replaceAll(regexSpecialCharacters, "").toLowerCase();
    }

    public static String[] chunkSplit(String original, int length) {
        List<String> chunks = new ArrayList<>();

        ByteArrayInputStream bis = new ByteArrayInputStream(original.getBytes());
        int n = 0;
        byte[] buffer = new byte[length];

        try {
            while ((n = bis.read(buffer)) > 0) {
                String result = "";
                for (byte b : buffer) {
                    result += (char) b;
                }
                Arrays.fill(buffer, (byte) 0);
                chunks.add(result.trim());
            }
        } catch (IOException e) {
            return null;
        }

        return chunks.toArray(new String[0]);
    }

    public static String getValidId(String url) {
        String encodedUrl = URLEncoder.encode(url);
        if (encodedUrl.length() < APPSEARCH_MAX_ID_LENGTH) {
            return URLEncoder.encode(url);
        }

        return encodedUrl.substring(0, Math.min(APPSEARCH_MAX_ID_LENGTH, encodedUrl.length()));
    }

    public static String processExpression(String expression, String replacementstring, String originalValue)  {
        final Pattern regExpPattern = Pattern.compile(expression);
        final Matcher m = regExpPattern.matcher(originalValue);
        String value = replacementstring;

        if (m.find()) {
            int groups = m.groupCount();
            for (int index = 0; index <= groups; index++) {
                String matchGroup = "$(" + index +")";
                if (value.indexOf(matchGroup) >= 0) {
                    value = value.replace("$(" + index +")", m.group(index));
                }
            }
            return value;
        }

        return originalValue;
    }
}
