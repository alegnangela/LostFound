package com.crescendo.lostfound.web;

/** Shared request-parameter bounds for paged list endpoints, so every one of them enforces the same limits. */
final class Paging {

    static final String DEFAULT_PAGE = "0";
    static final String DEFAULT_SIZE = "20";
    static final int MAX_SIZE = 100;

    private Paging() {
    }
}
