package com.empresa.inventario.Enums;

import java.util.EnumSet;

public enum Reason {
    PRODUCTION,
    MATERIAL_PURCHASE,
    CUSTOMER_RETURN,
    SALE,
    WASTE,
    INTERNAL_USE,
    GIFT,
    LOSS;

    private static final EnumSet<Reason> INBOUND_REASONS = EnumSet.of(
            PRODUCTION, MATERIAL_PURCHASE, CUSTOMER_RETURN);

    public boolean isInbound() {
        return INBOUND_REASONS.contains(this);
    }

    public boolean isOutbound() {
        return !isInbound();
    }
}
