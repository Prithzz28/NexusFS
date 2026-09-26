package com.dfs.master.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Request body for initiating a file upload.
 */
public record CreateFileRequest(
        @NotBlank(message = "File name is required")
        @Size(max = 255, message = "File name must not exceed 255 characters")
        String fileName,

        @Positive(message = "File size must be positive")
        long fileSize,

        @Size(max = 100, message = "Content type must not exceed 100 characters")
        String contentType
) {
}
