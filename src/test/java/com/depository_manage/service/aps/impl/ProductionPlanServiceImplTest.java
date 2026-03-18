package com.depository_manage.service.aps.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionPlanServiceImplTest {

    @Test
    void buildNormalizedOrderKey_shouldTrimAndCollapseWhitespace() {
        String key = ProductionPlanServiceImpl.buildNormalizedOrderKey("  昆山NSK  ", " LA ", " 6200VV*XC2  ");

        assertEquals("昆山NSK|LA|6200VV*XC2", key);
    }

    @Test
    void normalizePlanKeyPart_shouldCollapseInternalWhitespace() {
        String normalized = ProductionPlanServiceImpl.normalizePlanKeyPart("  昆山   NSK   ");

        assertEquals("昆山 NSK", normalized);
    }
}
