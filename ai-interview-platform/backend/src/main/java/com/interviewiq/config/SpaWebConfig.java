package com.interviewiq.config;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

/**
 * Serves the built React app (copied into {@code classpath:/static/} at package time) and
 * forwards every unknown non-API path to {@code index.html} so client-side routes work:
 * {@code /login}, {@code /oa/DEMO26}, {@code /assessments}, …
 *
 * <p>The API is untouched: {@code /api/**}, {@code /actuator/**} and {@code /h2-console/**}
 * are excluded here, so an unknown API path still gets the normal JSON 404 rather than a
 * page of HTML. When no frontend build is present (backend-only dev runs) this resolver
 * returns nothing and the app behaves exactly as before.
 */
@Configuration
public class SpaWebConfig implements WebMvcConfigurer {

    private static final String[] BACKEND_PREFIXES = {"api", "actuator", "h2-console", "error"};

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        String path = resourcePath.startsWith("/") ? resourcePath.substring(1) : resourcePath;
                        for (String prefix : BACKEND_PREFIXES) {
                            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                                return null;   // let the API answer (401/404 JSON), don't serve the SPA
                            }
                        }

                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) {
                            return requested;   // real asset: js / css / favicon / images
                        }

                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;   // SPA route → index.html
                    }
                });
    }
}
