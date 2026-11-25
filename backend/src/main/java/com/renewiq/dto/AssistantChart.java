package com.renewiq.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Chart values are calculated by the server, never supplied by the language model. */
public record AssistantChart(String type, String title, String xLabel, String yLabel,
                             String unit, LocalDate asOf, String note, List<Point> points) {
    public record Point(String label, BigDecimal value) { }
}
