package com.depository_manage.utils;

import org.springframework.util.StringUtils;

public final class CraftMappingUtil {

    private CraftMappingUtil() {
    }

    public static final String BAR_CRAFT = "棒材工艺";
    public static final String PIPE_CRAFT = "管材工艺";
    public static final String FORGING_CRAFT = "锻造工艺";

    public static String normalizeCraft(String craft) {
        if (!StringUtils.hasText(craft)) {
            return null;
        }
        if (BAR_CRAFT.equals(craft) || PIPE_CRAFT.equals(craft) || FORGING_CRAFT.equals(craft)) {
            return craft;
        }
        return null;
    }

    public static String inferCraftBySize(String size) {
        if (!StringUtils.hasText(size)) {
            return null;
        }
        String normalized = size.trim().replace(" ", "");

        if (normalized.contains("锻造")) {
            return FORGING_CRAFT;
        }
        if (normalized.contains("*") || normalized.contains("×")
                || normalized.contains("x") || normalized.contains("X")) {
            return PIPE_CRAFT;
        }
        if (normalized.matches("^\\d+(\\.\\d+)?$")) {
            return BAR_CRAFT;
        }
        return null;
    }
}
