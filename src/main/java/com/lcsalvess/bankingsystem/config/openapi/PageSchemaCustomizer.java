package com.lcsalvess.bankingsystem.config.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import static com.lcsalvess.bankingsystem.config.openapi.OpenApiConstants.JSON_MEDIA_TYPE;

/**
 * Documents the pagination metadata of paginated responses.
 *
 * <p>Describes the {@code PageMetadata} properties and adds a page example to
 * a private copy of the response schema of the paginated endpoints, so the
 * shared schema is never changed. Reads the {@code application/json} content
 * of the 200 response, so it must run after {@link JsonContentTypeCustomizer}.
 */
@Component
@Order(OpenApiConstants.Order.PAGE_SCHEMA)
public class PageSchemaCustomizer implements OpenApiCustomizer {

    private static final String PAGE_METADATA_SCHEMA = "PageMetadata";
    private static final String COMPONENT_SCHEMA_PREFIX = "#/components/schemas/";
    private static final String USERS_PATH = "/api/v1/users";
    private static final String AUDIT_EVENTS_PATH = "/api/v1/audit-events";

    @Override
    public void customise(OpenAPI openApi) {
        customizePageMetadata(openApi);
        customizePageExamples(openApi);
    }

    private void customizePageMetadata(OpenAPI openApi) {
        if (openApi.getComponents() == null
                || openApi.getComponents().getSchemas() == null) {
            return;
        }

        Schema<?> pageMetadata = openApi.getComponents()
                .getSchemas()
                .get(PAGE_METADATA_SCHEMA);

        if (pageMetadata == null || pageMetadata.getProperties() == null) {
            return;
        }

        setProperty(
                pageMetadata,
                "size",
                "Number of elements per page. The default is endpoint-specific.",
                10
        );

        setProperty(
                pageMetadata,
                "number",
                "Zero-based index of the current page.",
                0
        );

        setProperty(
                pageMetadata,
                "totalElements",
                "Total number of elements matching the query.",
                10
        );

        setProperty(
                pageMetadata,
                "totalPages",
                "Total number of available pages.",
                1
        );
    }

    private void customizePageExamples(OpenAPI openApi) {
        if (openApi.getPaths() == null) {
            return;
        }

        customizePageExample(openApi, USERS_PATH);
        customizePageExample(openApi, AUDIT_EVENTS_PATH);
    }

    private void customizePageExample(
            OpenAPI openApi,
            String path
    ) {
        PathItem pathItem = openApi.getPaths().get(path);

        if (pathItem == null || pathItem.getGet() == null) {
            return;
        }

        ApiResponse response = pathItem.getGet()
                .getResponses()
                .get("200");

        if (response == null
                || response.getContent() == null
                || response.getContent().get(JSON_MEDIA_TYPE) == null) {
            return;
        }

        MediaType json = response.getContent().get(JSON_MEDIA_TYPE);
        Schema<?> responseSchema = resolveSchema(json.getSchema(), openApi);

        if (responseSchema == null || responseSchema.getProperties() == null) {
            return;
        }

        if (json.getSchema() != null && json.getSchema().get$ref() != null) {
            ObjectSchema isolatedSchema = copyObjectSchema(responseSchema);
            json.setSchema(isolatedSchema);
            responseSchema = isolatedSchema;
        }

        Schema<?> pageProperty = responseSchema.getProperties().get("page");

        if (pageProperty == null) {
            return;
        }

        Schema<?> pageSchema = resolveSchema(pageProperty, openApi);

        if (pageSchema == null || pageSchema.getProperties() == null) {
            return;
        }

        responseSchema.addProperty(
                "page",
                createPageSchema(pageSchema)
        );
    }

    private static ObjectSchema createPageSchema(Schema<?> source) {
        ObjectSchema page = copyObjectSchema(source);

        LinkedHashMap<String, Object> example = new LinkedHashMap<>();
        example.put("size", 20);
        example.put("number", 0);
        example.put("totalElements", 10);
        example.put("totalPages", 1);

        page.setExample(example);

        return page;
    }

    private static ObjectSchema copyObjectSchema(Schema<?> source) {
        ObjectSchema copy = new ObjectSchema();

        copy.setDescription(source.getDescription());

        if (source.getTitle() != null) {
            copy.setTitle(source.getTitle());
        }

        if (source.getProperties() != null) {
            copy.setProperties(
                    new LinkedHashMap<>(source.getProperties())
            );
        }

        if (source.getRequired() != null) {
            copy.setRequired(
                    new ArrayList<>(source.getRequired())
            );
        }

        return copy;
    }

    private Schema<?> resolveSchema(
            Schema<?> schema,
            OpenAPI openApi
    ) {
        if (schema == null || schema.get$ref() == null) {
            return schema;
        }

        String reference = schema.get$ref();

        if (!reference.startsWith(COMPONENT_SCHEMA_PREFIX)
                || openApi.getComponents() == null
                || openApi.getComponents().getSchemas() == null) {
            return null;
        }

        String schemaName = reference.substring(COMPONENT_SCHEMA_PREFIX.length());

        return openApi.getComponents()
                .getSchemas()
                .get(schemaName);
    }

    private static void setProperty(
            Schema<?> parent,
            String propertyName,
            String description,
            Object example
    ) {
        Schema<?> property = parent.getProperties().get(propertyName);

        if (property == null) {
            return;
        }

        property.setDescription(description);
        property.setExample(example);
    }
}
