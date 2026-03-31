package com.pos.system.dto;

import lombok.*;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    private List<T> content;
    private int     page;
    private int     size;
    private long    totalElements;
    private int     totalPages;
    private boolean first;
    private boolean last;
    private boolean hasNext;
    private boolean hasPrevious;

    public static <T> PageResponse<T> of(
            List<T> content, int page, int size, long total) {
        int totalPages = (int) Math.ceil((double) total / size);
        return PageResponse.<T>builder()
                .content(content)
                .page(page)
                .size(size)
                .totalElements(total)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .build();
    }
}
