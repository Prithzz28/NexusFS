package com.dfs.master.dto;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Paginated wrapper for file list views.
 */
public record FileListResponse(
        List<FileResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean last
) {
    public static FileListResponse fromPage(Page<FileResponse> page) {
        return new FileListResponse(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isLast()
        );
    }
}
