package com.lcsalvess.bankingsystem.config.openapi;

import com.lcsalvess.bankingsystem.util.pagination.SortableBy;
import io.swagger.v3.oas.models.Operation;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;

/**
 * Customizes OpenAPI documentation for pagination sorting parameters.
 *
 * <p>Uses {@link SortableBy} annotations to document the sorting properties
 * allowed by each controller endpoint.
 */
@Component
public class SortParameterCustomizer implements OperationCustomizer {

  private static final String SORT_PARAMETER = "sort";

  /**
   * Customizes the description of the sorting parameter for an API operation.
   *
   * <p>Documents the supported sorting format and the properties allowed
   * by the handler method's {@link SortableBy} annotation.
   *
   * @param operation the OpenAPI operation being customized
   * @param handlerMethod the controller method associated with the operation
   * @return the customized OpenAPI operation
   */
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
