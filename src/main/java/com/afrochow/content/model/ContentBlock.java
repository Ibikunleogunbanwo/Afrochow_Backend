package com.afrochow.content.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * One block inside a policy section. Which fields are set depends on {@code type}:
 * <ul>
 *   <li>{@code paragraph} — {@code text}, which may contain {@code **bold**} and {@code [label](href)}</li>
 *   <li>{@code list} — {@code items}</li>
 *   <li>{@code table} — {@code columns} and {@code rows}</li>
 *   <li>{@code contact} — {@code label} and {@code email}</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContentBlock(
        String type,
        String text,
        List<String> items,
        List<String> columns,
        List<List<String>> rows,
        String label,
        String email
) {
}
