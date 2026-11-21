package ee.taltech.ite4120.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Tells the generated API contract what {@link SecurityConfig} enforces: every
 * {@code /api/**} endpoint wants {@code Authorization: Bearer <username>}.
 *
 * <p>Without this, Swagger UI happily generates header-less curl commands whose
 * only answer is a 401 — the single most reported confusion in the template.
 * With it, every operation carries a padlock, the <em>Authorize</em> button
 * accepts the username, and "Try it out" sends the header.
 */
@Configuration
public class OpenApiConfig {

    private static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI apiDescription() {
        return new OpenAPI()
                .info(new Info()
                        .title("Vessel and License Register — ITE4120")
                        .description("""
                                Official state register of maritime vessels, operational licenses, \
                                ownership history, and retirement records (with live X-Road population registry lookup). \
                                Every `/api` endpoint requires `Authorization: Bearer <username>`. \
                                In local mock mode any username works — click **Authorize** and type one.""")
                        .version("0.1.0"))
                .tags(List.of(
                        new Tag().name("Vessels")
                                .description("The vessel register: registration, search, update, retirement, owner lookup"),
                        new Tag().name("Vessel licenses")
                                .description("License history, issuing, cancellation (DELETE) and revocation per vessel"),
                        new Tag().name("Vessel ownership")
                                .description("Dated ownership periods and live-verified transfers"),
                        new Tag().name("Licenses")
                                .description("Register-wide license monitoring")))
                .components(new Components().addSecuritySchemes(BEARER,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .description("Any username in local mock mode — e.g. plain `aleksander`.")))
                // Global: every operation inherits the requirement, matching the
                // security chain's `/api/**` rule.
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    /** Primary Swagger UI definition: our Vessel and License Register solution. */
    @Bean
    public GroupedOpenApi vesselsApi() {
        return GroupedOpenApi.builder()
                .group("1-vessels-register")
                .displayName("Vessel and License Register (vessels)")
                .packagesToScan("ee.taltech.ite4120.vessels")
                .build();
    }

    /** Background reference definition: the course template's Animals Register package. */
    @Bean
    public GroupedOpenApi animalsTemplateApi() {
        return GroupedOpenApi.builder()
                .group("2-animals-template")
                .displayName("Animals Register (course template)")
                .packagesToScan("ee.taltech.ite4120.animals")
                .pathsToExclude("/mock-registry/**", "/api/uma/**")
                .build();
    }
}
