package com.rto.core;

public final class Names {
    private Names() {}

    /** phonePrimary -> phone_primary (error field names match the JSON the client sent). */
    public static String snake(String camel) {
        StringBuilder sb = new StringBuilder();
        for (char c : camel.toCharArray()) {
            if (Character.isUpperCase(c)) sb.append('_').append(Character.toLowerCase(c));
            else sb.append(c);
        }
        return sb.toString();
    }

    public static String snakePath(String path) {
        String[] parts = path.split("\\.");
        for (int i = 0; i < parts.length; i++) parts[i] = snake(parts[i].replaceAll("\\[\\d+]", ""));
        return String.join(".", parts);
    }
}
