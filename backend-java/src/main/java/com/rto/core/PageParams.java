package com.rto.core;

/** Standard collection query parameters: page, page_size (<= 100), search, sort, order. */
public record PageParams(int page, int pageSize, String search, String sort, String order) {
    public static final int MAX_PAGE_SIZE = 100;

    public boolean desc() {
        return "desc".equals(order);
    }

    public int offset() {
        return (page - 1) * pageSize;
    }

    public static PageParams of(int page, int pageSize) {
        return new PageParams(page, pageSize, null, null, "asc");
    }
}
