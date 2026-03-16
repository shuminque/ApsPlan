package com.depository_manage.entity.aps;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 生产订单状态统一定义（编码 + 文案）。
 */
public enum ProductionOrderStatus {
    PENDING("0", "待排产", new String[]{"0", "待排产"}),
    PLANNED("1", "已排产", new String[]{"1", "已排产"}),
    COMPLETED("2", "已完成", new String[]{"2", "已完成"});

    private final String code;
    private final String label;
    private final String[] aliases;

    ProductionOrderStatus(String code, String label, String[] aliases) {
        this.code = code;
        this.label = label;
        this.aliases = aliases;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public String[] getAliases() {
        return aliases;
    }

    public static List<String> openStatusFilterValues() {
        return Arrays.asList(
                PENDING.code, PENDING.label,
                PLANNED.code, PLANNED.label
        );
    }

    public static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        for (ProductionOrderStatus status : values()) {
            for (String alias : status.aliases) {
                if (alias.equals(trimmed)) {
                    return status.code;
                }
            }
        }
        return trimmed;
    }

    public static List<String> aliasesFor(String value) {
        if (value == null) {
            return Collections.emptyList();
        }
        String normalized = normalize(value);
        for (ProductionOrderStatus status : values()) {
            if (status.code.equals(normalized)) {
                return Arrays.asList(status.aliases);
            }
        }
        return Collections.singletonList(value);
    }
}
