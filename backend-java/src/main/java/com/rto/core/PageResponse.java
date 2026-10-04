package com.rto.core;

import java.util.List;
import java.util.function.Function;

/** {"items": [], "page": 1, "page_size": 20, "total": 100, "pages": 5} */
public record PageResponse<T>(List<T> items, int page, int pageSize, long total, int pages) {

    public static <T> PageResponse<T> of(List<T> items, PageParams p, long total) {
        return new PageResponse<>(items, p.page(), p.pageSize(), total,
                total == 0 ? 0 : (int) Math.ceil((double) total / p.pageSize()));
    }

    public <R> PageResponse<R> map(Function<T, R> fn) {
        return new PageResponse<>(items.stream().map(fn).toList(), page, pageSize, total, pages);
    }
}
