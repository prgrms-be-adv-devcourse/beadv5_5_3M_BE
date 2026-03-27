package com.example.ticketservice.presentation.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Ticket Service API")
                        .description("티켓 예매/관리 서비스")
                        .version("v1"));
    }

    @Bean
    public OperationCustomizer xUserIdHeaderCustomizer() {
        return (operation, handlerMethod) -> {
            operation.addParametersItem(
                    new Parameter()
                            .in("header")
                            .name("X-User-Id")
                            .description("Gateway에서 주입되는 사용자 ID (UUID)")
                            .required(true)
                            .schema(new StringSchema().format("uuid"))
            );
            return operation;
        };
    }
}