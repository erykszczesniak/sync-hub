package com.erykszczesniak.synchub.config

import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityScheme
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {
    @Bean
    fun openApi(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("sync-hub API")
                    .version("v1")
                    .description(
                        "Control and status API of the System-to-System Sync Hub. " +
                            "Status endpoints are read-only and open; control endpoints require HTTP Basic.",
                    ).license(License().name("MIT")),
            ).components(
                Components().addSecuritySchemes(
                    BASIC_AUTH,
                    SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("basic"),
                ),
            )

    companion object {
        const val BASIC_AUTH = "basicAuth"
    }
}
