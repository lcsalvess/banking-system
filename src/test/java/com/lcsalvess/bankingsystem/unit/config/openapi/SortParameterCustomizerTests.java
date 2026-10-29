package com.lcsalvess.bankingsystem.unit.config.openapi;

import com.lcsalvess.bankingsystem.config.openapi.SortParameterCustomizer;
import com.lcsalvess.bankingsystem.util.pagination.SortableBy;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.web.method.HandlerMethod;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("SortParameterCustomizer")
class SortParameterCustomizerTests {

    private static final String PROVIDER =
            "com.lcsalvess.bankingsystem.unit.config.openapi.SortParameterCustomizerTests#";

    private final SortParameterCustomizer customizer = new SortParameterCustomizer();

    @Nested
    @DisplayName("When the handler method has @SortableBy")
    class SortableHandlerTests {

        @ParameterizedTest(name = "{0}")
        @MethodSource(PROVIDER + "sortableValues")
        @DisplayName("Should describe the allowed sort properties")
        void shouldDescribeTheAllowedSortProperties(
                String scenario,
                String value,
                String expectedProperties
        ) {
            Parameter sort = new Parameter().name("sort");
            Operation operation = operationWith(sort);

            customizer.customize(operation, handlerMethodWith(value));

            assertEquals(
                    "Sorting criteria in the format property,(asc|desc). "
                            + "Multiple criteria are supported. "
                            + "Allowed properties: " + expectedProperties + ".",
                    sort.getDescription()
            );
        }

        @Test
        @DisplayName("Should only change the sort parameter")
        void shouldOnlyChangeTheSortParameter() {
            Parameter page = new Parameter().name("page").description("Page index.");
            Parameter sort = new Parameter().name("sort");
            Parameter size = new Parameter().name("size");
            Operation operation = operationWith(page, sort, size);

            customizer.customize(operation, handlerMethodWith("id,name"));

            assertEquals("Page index.", page.getDescription());
            assertNull(size.getDescription());
            assertEquals(
                    "Sorting criteria in the format property,(asc|desc). "
                            + "Multiple criteria are supported. "
                            + "Allowed properties: id, name.",
                    sort.getDescription()
            );
        }

        @Test
        @DisplayName("Should replace an existing description of the sort parameter")
        void shouldReplaceAnExistingDescription() {
            Parameter sort = new Parameter().name("sort").description("Old description.");
            Operation operation = operationWith(sort);

            customizer.customize(operation, handlerMethodWith("id"));

            assertEquals(
                    "Sorting criteria in the format property,(asc|desc). "
                            + "Multiple criteria are supported. "
                            + "Allowed properties: id.",
                    sort.getDescription()
            );
        }

        @Test
        @DisplayName("Should leave the operation untouched when it has no sort parameter")
        void shouldLeaveTheOperationUntouchedWithoutSortParameter() {
            Parameter page = new Parameter().name("page");
            Operation operation = operationWith(page);

            customizer.customize(operation, handlerMethodWith("id"));

            assertNull(page.getDescription());
        }

        @Test
        @DisplayName("Should return the same operation")
        void shouldReturnTheSameOperation() {
            Operation operation = operationWith(new Parameter().name("sort"));

            assertSame(operation, customizer.customize(operation, handlerMethodWith("id")));
        }

        @Test
        @DisplayName("Should not fail when the operation has no parameters")
        void shouldNotFailWhenTheOperationHasNoParameters() {
            Operation operation = new Operation();

            assertSame(operation, customizer.customize(operation, handlerMethodWith("id")));
            assertNull(operation.getParameters());
        }
    }

    @Nested
    @DisplayName("When the handler method has no @SortableBy")
    class NonSortableHandlerTests {

        @Test
        @DisplayName("Should leave the sort parameter untouched")
        void shouldLeaveTheSortParameterUntouched() {
            Parameter sort = new Parameter().name("sort").description("Original.");
            Operation operation = operationWith(sort);
            HandlerMethod handlerMethod = mock(HandlerMethod.class);
            when(handlerMethod.getMethodAnnotation(SortableBy.class)).thenReturn(null);

            Operation result = customizer.customize(operation, handlerMethod);

            assertSame(operation, result);
            assertEquals("Original.", sort.getDescription());
        }

        @Test
        @DisplayName("Should not fail when the operation has no parameters")
        void shouldNotFailWhenTheOperationHasNoParameters() {
            Operation operation = new Operation();
            HandlerMethod handlerMethod = mock(HandlerMethod.class);
            when(handlerMethod.getMethodAnnotation(SortableBy.class)).thenReturn(null);

            assertSame(operation, customizer.customize(operation, handlerMethod));
        }
    }

    @SuppressWarnings("unused")
    static Stream<Arguments> sortableValues() {
        return Stream.of(
                Arguments.of("single property", "id", "id"),
                Arguments.of("several properties", "id,name,createdAt", "id, name, createdAt"),
                Arguments.of("spaces around commas", "id , name ,  createdAt", "id, name, createdAt"),
                Arguments.of("leading and trailing spaces", "  id,name  ", "id, name")
        );
    }

    private static Operation operationWith(Parameter... parameters) {
        Operation operation = new Operation();
        operation.setParameters(new ArrayList<>(List.of(parameters)));
        return operation;
    }

    private static HandlerMethod handlerMethodWith(String value) {
        SortableBy sortableBy = mock(SortableBy.class);
        when(sortableBy.value()).thenReturn(value);

        HandlerMethod handlerMethod = mock(HandlerMethod.class);
        when(handlerMethod.getMethodAnnotation(SortableBy.class)).thenReturn(sortableBy);

        return handlerMethod;
    }
}
