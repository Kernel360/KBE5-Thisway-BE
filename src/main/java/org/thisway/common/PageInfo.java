package org.thisway.common;

import org.springframework.data.domain.Page;

public record PageInfo(
        long totalElements,
        int numberOfElements,
        int totalPages,
        int currentPage,
        int size
) {

    public static PageInfo from(Page<?> page) {
        return new PageInfo(
                page.getTotalElements(),
                page.getNumberOfElements(),
                page.getTotalPages(),
                page.getNumber(),
                page.getSize()
        );
    }
}
