package com.lcsalvess.bankingsystem.config.openapi;

import com.lcsalvess.bankingsystem.util.pagination.SortableBy;
import io.swagger.v3.oas.models.Operation;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

import java.util.Arrays;
import java.util.stream.Collectors;

@Component
public class SortParameterCustomizer implements OperationCustomizer {

    private static final String SORT_PARAMETER = "sort";

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        SortableBy sortableBy = handlerMethod.getMethodAnnotation(SortableBy.class);

        if (sortableBy == null || operation.getParameters() == null) {
            return operation;
        }

        String properties = Arrays.stream(sortableBy.value().split(","))
                .map(String::trim)
                .collect(Collectors.joining(", "));

        operation.getParameters().stream()
                .filter(parameter -> SORT_PARAMETER.equals(parameter.getName()))
                .forEach(parameter -> parameter.setDescription(
                        "Sorting criteria in the format property,(asc|desc). "
                                + "Multiple criteria are supported. "
                                + "Allowed properties: " + properties + "."
                ));

        return operation;
    }
}