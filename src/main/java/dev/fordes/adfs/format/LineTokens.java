package dev.fordes.adfs.format;

import java.util.ArrayList;
import java.util.List;

public final class LineTokens {

    private LineTokens() {
    }

    public static List<String> beforeComment(String line) {
        List<String> tokens = new ArrayList<>();
        int index = 0;
        while (index < line.length()) {
            while (index < line.length() && Character.isWhitespace(line.charAt(index))) {
                index++;
            }
            if (index == line.length() || line.charAt(index) == '#') {
                return List.copyOf(tokens);
            }
            int start = index;
            while (index < line.length() && !Character.isWhitespace(line.charAt(index)) && line.charAt(index) != '#') {
                index++;
            }
            tokens.add(line.substring(start, index));
            if (index < line.length() && line.charAt(index) == '#') {
                return List.copyOf(tokens);
            }
        }
        return List.copyOf(tokens);
    }
}
