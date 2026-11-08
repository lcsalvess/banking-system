package com.lcsalvess.bankingsystem.config.openapi;

import com.lcsalvess.bankingsystem.dto.response.exception.ApiErrorResponse;
import com.lcsalvess.bankingsystem.exception.messages.ApiErrorMessages;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.utils.SpringDocAnnotationsUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.lcsalvess.bankingsystem.config.openapi.OpenApiConstants.JSON_MEDIA_TYPE;

/**
 * Documents the error responses shared by every operation.
 *
 * <p>Registers the {@code ApiErrorResponse} schema, adds the standard error
 * responses that an operation does not declare, and gives every 4xx and 5xx
 * response the error schema and a matching example.
 */
@Component
@Order(OpenApiConstants.Order.ERROR_RESPONSES)
public class ErrorResponsesCustomizer implements OpenApiCustomizer {

    private static final String LOGIN_PATH = "/api/v1/auth/login";

    @Override
    public void customise(OpenAPI openApi) {
        registerErrorSchema(openApi);

        openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperationsMap().forEach((httpMethod, operation) ->
                        customizeOperation(path, httpMethod, operation)
                )
        );
    }

    private void registerErrorSchema(OpenAPI openApi) {
        if (openApi.getComponents() == null) {
            openApi.setComponents(new Components());
        }

        if (openApi.getComponents().getSchemas() == null) {
            openApi.getComponents().setSchemas(new LinkedHashMap<>());
        }

        if (!openApi.getComponents().getSchemas()
                .containsKey(ApiErrorResponse.class.getSimpleName())) {

            SpringDocAnnotationsUtils.extractSchema(
                    openApi.getComponents(),
                    ApiErrorResponse.class,
                    null,
                    null,
                    openApi.getSpecVersion()
            );
        }
    }

    private void customizeOperation(
            String path,
            PathItem.HttpMethod httpMethod,
            Operation operation
    ) {
        boolean isPublicLogin =
                LOGIN_PATH.equals(path)
                        && PathItem.HttpMethod.POST.equals(httpMethod);

        if (!isPublicLogin) {
            addResponseIfAbsent(
                    operation,
                    "401",
                    "Unauthorized - Authentication is required or the JWT is invalid."
            );

            addResponseIfAbsent(
                    operation,
                    "403",
                    "Forbidden - Insufficient permissions to access this resource."
            );
        }

        addResponseIfAbsent(
                operation,
                "405",
                "Method Not Allowed - The HTTP method is not supported."
        );

        addResponseIfAbsent(
                operation,
                "415",
                "Unsupported Media Type - The request content type is not supported."
        );

        addResponseIfAbsent(
                operation,
                "500",
                "Unexpected internal server error."
        );

        addResponseIfAbsent(
                operation,
                "503",
                "Service Unavailable - A required infrastructure dependency "
                        + "is temporarily unavailable."
        );

        operation.getResponses().forEach((status, response) -> {
            if (status.matches("[45]\\d\\d")) {
                String message = isPublicLogin && "401".equals(status)
                        ? ApiErrorMessages.INVALID_CREDENTIALS
                        : errorMessage(status);

                response.setContent(errorContent(status, message));
            }
        });
    }

    private void addResponseIfAbsent(
            Operation operation,
            String status,
            String description
    ) {
        ApiResponse response = operation.getResponses().get(status);

        if (response == null) {
            operation.getResponses().addApiResponse(
                    status,
                    new ApiResponse()
                            .description(description)
                            .content(errorContent(status, errorMessage(status)))
            );
            return;
        }

        if (response.getContent() == null
                || response.getContent().isEmpty()) {
            response.setContent(errorContent(status, errorMessage(status)));
        }
    }

    private Content errorContent(String status, String message) {
        return new Content()
                .addMediaType(
                        JSON_MEDIA_TYPE,
                        new MediaType()
                                .schema(new Schema<>()
                                        .$ref("#/components/schemas/ApiErrorResponse"))
                                .example(Map.of(
                                        "status", Integer.parseInt(status),
                                        "message", message,
                                        "timestamp", "2026-10-15T18:30:00Z"
                                ))
                );
    }

    private String errorMessage(String status) {
        return switch (status) {
            case "400" -> "Request validation failed.";
            case "401" -> "Authentication is required or the JWT is invalid.";
            case "403" -> "Insufficient permissions to access this resource.";
            case "404" -> "The requested resource was not found.";
            case "405" -> "The HTTP method is not supported.";
            case "409" -> "The request conflicts with the current resource state.";
            case "415" -> "The request content type is not supported.";
            case "429" -> "Too many login attempts. Please wait before trying again.";
            case "503" -> "Service temporarily unavailable.";
            default -> "Unexpected internal server error.";
        };
    }
}
