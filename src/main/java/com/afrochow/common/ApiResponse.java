package com.afrochow.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "Standard API response wrapper")
public class ApiResponse<T> {

    @Schema(example = "true")
    private Boolean success;

    @Schema(example = "Operation successful")
    private String message;

    private T data;

    // ==================== ERROR FIELDS (ERROR RESPONSES ONLY) ====================

    @Schema(hidden = true)
    private String errorCode;

    @Schema(hidden = true)
    private Map<String, String> validationErrors;

    // ==================== META ====================

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS")
    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // ==================== SUCCESS RESPONSES ====================

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("Operation successful")
                .data(data)
                .errorCode(null)
                .validationErrors(null)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .errorCode(null)
                .validationErrors(null)
                .build();
    }

    public static <T> ApiResponse<T> success(String message) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .errorCode(null)
                .validationErrors(null)
                .build();
    }

    public static <T> ApiResponse<PageResponse<T>> successPage(Page<T> page) {
        return ApiResponse.<PageResponse<T>>builder()
                .success(true)
                .message("Data retrieved successfully")
                .data(PageResponse.from(page))
                .errorCode(null)
                .validationErrors(null)
                .build();
    }

    public static <T> ApiResponse<PageResponse<T>> successPage(String message, Page<T> page) {
        return ApiResponse.<PageResponse<T>>builder()
                .success(true)
                .message(message)
                .data(PageResponse.from(page))
                .errorCode(null)
                .validationErrors(null)
                .build();
    }

    // ==================== ERROR RESPONSES ====================

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode(errorCode)
                .build();
    }

    public static <T> ApiResponse<T> badRequest(String message) {
        return error(message, "BAD_REQUEST");
    }

    public static <T> ApiResponse<T> unauthorized(String message) {
        return error(message != null ? message : "Unauthorized access", "UNAUTHORIZED");
    }

    public static <T> ApiResponse<T> forbidden(String message) {
        return error(message != null ? message : "Access forbidden", "FORBIDDEN");
    }

    public static <T> ApiResponse<T> notFound(String resource) {
        return error(resource + " not found", "NOT_FOUND");
    }

    public static <T> ApiResponse<T> conflict(String message) {
        return error(message, "CONFLICT");
    }

    public static <T> ApiResponse<T> internalError(String message) {
        return error(
                message != null ? message : "Internal server error",
                "INTERNAL_ERROR"
        );
    }

    public static <T> ApiResponse<T> validationError(
            String message,
            Map<String, String> errors
    ) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errorCode("VALIDATION_ERROR")
                .validationErrors(errors)
                .build();
    }

    // ==================== PAGINATION ====================

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Paginated response")
    public static class PageResponse<T> {

        private java.util.List<T> content;
        private Integer pageNumber;
        private Integer pageSize;
        private Long totalElements;
        private Integer totalPages;
        private Boolean last;
        private Boolean first;
        private Boolean hasNext;
        private Boolean hasPrevious;

        public static <T> PageResponse<T> from(Page<T> page) {
            return PageResponse.<T>builder()
                    .content(page.getContent())
                    .pageNumber(page.getNumber())
                    .pageSize(page.getSize())
                    .totalElements(page.getTotalElements())
                    .totalPages(page.getTotalPages())
                    .last(page.isLast())
                    .first(page.isFirst())
                    .hasNext(page.hasNext())
                    .hasPrevious(page.hasPrevious())
                    .build();
        }
    }
}
