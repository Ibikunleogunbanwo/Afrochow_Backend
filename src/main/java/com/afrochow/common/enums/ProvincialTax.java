package com.afrochow.common.enums;

import java.math.BigDecimal;

/**
 * Canadian provincial tax rates.
 * Mirrors the PROVINCIAL_TAX map in the frontend CheckoutPage.
 * Used by OrderService to calculate tax server-side — never trust the frontend total.
 */
public enum ProvincialTax {

    AB("Alberta",                    "GST",       new BigDecimal("0.05")),
    BC("British Columbia",           "GST + PST", new BigDecimal("0.12")),
    MB("Manitoba",                   "GST + PST", new BigDecimal("0.12")),
    NB("New Brunswick",              "HST",       new BigDecimal("0.15")),
    NL("Newfoundland and Labrador",  "HST",       new BigDecimal("0.15")),
    NS("Nova Scotia",                "HST",       new BigDecimal("0.15")),
    ON("Ontario",                    "HST",       new BigDecimal("0.13")),
    PE("Prince Edward Island",       "HST",       new BigDecimal("0.15")),
    QC("Quebec",                     "GST + QST", new BigDecimal("0.14975")),
    SK("Saskatchewan",               "GST + PST", new BigDecimal("0.11"));

    private final String provinceName;
    private final String taxLabel;
    private final BigDecimal rate;

    ProvincialTax(String provinceName, String taxLabel, BigDecimal rate) {
        this.provinceName = provinceName;
        this.taxLabel     = taxLabel;
        this.rate         = rate;
    }

    public String getProvinceName() { return provinceName; }
    public String getTaxLabel()     { return taxLabel;     }
    public BigDecimal getRate()     { return rate;         }

    /**
     * Look up by province code, defaulting to AB (5% GST) if unknown.
     */
    public static ProvincialTax fromCode(String code) {
        if (code == null) return AB;
        try {
            return ProvincialTax.valueOf(code.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            return AB;
        }
    }
}