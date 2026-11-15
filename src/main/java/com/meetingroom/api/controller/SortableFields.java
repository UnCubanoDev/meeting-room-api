package com.meetingroom.api.controller;

import com.meetingroom.api.exception.InvalidSortException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Map;

/**
 * Traduce los nombres de ordenacion de la API a propiedades de la entidad y rechaza el resto.
 *
 * <p>Sin esta lista blanca, {@code ?sort=loquesea} llega hasta JPA y termina en un 500; ademas
 * permite que el cliente ordene por el mismo nombre que ve en el JSON ({@code start}) aunque la
 * propiedad interna se llame distinto ({@code startTime}).</p>
 */
final class SortableFields {

    private final Map<String, String> apiToEntity;

    private SortableFields(Map<String, String> apiToEntity) {
        this.apiToEntity = apiToEntity;
    }

    static SortableFields of(Map<String, String> apiToEntity) {
        return new SortableFields(Map.copyOf(apiToEntity));
    }

    Pageable apply(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return pageable;
        }
        Sort translated = Sort.by(pageable.getSort().stream()
                .map(order -> {
                    String property = apiToEntity.get(order.getProperty());
                    if (property == null) {
                        throw new InvalidSortException(order.getProperty(), apiToEntity.keySet().stream().sorted().toList());
                    }
                    return order.withProperty(property);
                })
                .toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), translated);
    }

}
