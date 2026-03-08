package com.afrochow.analytics.dto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RevenueReportDto {

    private LocalDate date;
    private BigDecimal revenue;
    private Integer orderCount;
    private BigDecimal averageOrderValue;
}