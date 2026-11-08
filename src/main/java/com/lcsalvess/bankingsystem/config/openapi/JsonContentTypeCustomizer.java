package com.lcsalvess.bankingsystem.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import static com.lcsalvess.bankingsystem.config.openapi.OpenApiConstants.JSON_MEDIA_TYPE;

/**
 * Exposes only {@code application/json} on successful (200 and 201) responses.
 *
 * <p>The first generated media type is kept as is and re-registered under
 * {@code application/json}. This customizer must run before the ones that
 * read the JSON content of these responses.
 */
@Component
@Order(OpenApiConstants.Order.JSON_CONTENT_TYPE)
public class JsonContentTypeCustomizer implements OpenApiCustomizer {

    @Override
    public void customise(OpenAPI openApi) {
        openApi.getPaths().forEach((path, pathItem) ->
                pathItem.readOperationsMap().forEach((httpMethod, operation) ->
                        operation.getResponses().forEach((status, response) -> {
                            if ("200".equals(status) || "201".equals(status)) {
                                keepOnlyJson(response);
                            }
                        })
                )
        );
    }

    private static void keepOnlyJson(ApiResponse response) {
        Content content = response.getContent();

        if (content == null || content.isEmpty()) {
            return;
        }

        MediaType mediaType = content.values().iterator().next();

        content.clear();
        content.addMediaType(JSON_MEDIA_TYPE, mediaType);
    }
}
