package com.afrochow.common.enums;

import lombok.Getter;

@Getter
public enum Role {
    CUSTOMER("CUS", "Customer who orders food"),
    VENDOR("VEN", "Restaurant/vendor who sells food"),
    ADMIN("ADM", "Platform administrator");

    private final String prefix;
    private final String description;

    Role(String prefix, String description) {
        this.prefix = prefix;
        this.description = description;
    }
}
