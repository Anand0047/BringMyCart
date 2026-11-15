package com.bringmycart.bmc.savedproducts.api.dto;

import java.util.List;

import org.springframework.data.domain.Page;

/** A deliberately stable API pagination shape, independent of Spring Data's internal JSON form. */
public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last
) {
    public static <T, R> PageResponse<R> from(Page<T> source, java.util.function.Function<T, R> mapper) {
        return new PageResponse<>(source.map(mapper).getContent(), source.getNumber(), source.getSize(),
            source.getTotalElements(), source.getTotalPages(), source.isFirst(), source.isLast());
    }
}
