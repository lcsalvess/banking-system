package com.lcsalvess.bankingsystem.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.jspecify.annotations.NonNull;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

import static com.lcsalvess.bankingsystem.config.openapi.OpenApiConstants.JSON_MEDIA_TYPE;

/**
 * Sets the 201 response example of the deposit, withdrawal and transfer
 * endpoints.
 *
 * <p>Reads the {@code application/json} content of the response, so it must
 * run after {@link JsonContentTypeCustomizer}.
 */
@Component
@Order(OpenApiConstants.Order.TRANSACTION_EXAMPLES)
public class TransactionExamplesCustomizer implements OpenApiCustomizer {

    private static final Map<String, String> TYPE_BY_PATH = Map.of(
            "/api/v1/transactions/deposit", "DEPOSIT",
            "/api/v1/transactions/withdraw", "WITHDRAWAL",
            "/api/v1/transactions/transfer", "TRANSFER_SENT"
    );

    @Override
    public void customise(OpenAPI openApi) {
        TYPE_BY_PATH.forEach((path, type) -> {
            PathItem pathItem = openApi.getPaths().get(path);

            if (pathItem == null || pathItem.getPost() == null) {
                return;
            }

            setExample(pathItem.getPost(), type);
        });
    }

    private void setExample(Operation operation, String type) {
        ApiResponse response = operation.getResponses().get("201");

        if (response == null || response.getContent() == null) {
            return;
        }

        MediaType json = response.getContent().get(JSON_MEDIA_TYPE);

        if (json == null) {
            return;
        }

        json.setExample(createExample(type));
    }

    private static @NonNull Map<String, Object> createExample(String type) {
        Map<String, Object> example = new LinkedHashMap<>();
        example.put(
                "transactionCode",
                "108cf2a1-bcee-460a-a5db-f9b231947411"
        );

        if ("TRANSFER_SENT".equals(type)) {
            example.put(
                    "transferCode",
                    "a12bc34d-5678-4abc-9def-1234567890ab"
            );
        }

        example.put("type", type);
        example.put("amount", 450.95);
        example.put("createdAt", "2026-10-15T14:30:00");
        return example;
    }
}
