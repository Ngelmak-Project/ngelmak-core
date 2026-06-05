package org.ngelmakproject.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

// @Configuration
public class StaticConfig implements WebMvcConfigurer {

  // @Value("${file.upload-directory.location}")
  private String location;
  // @Value("${file.public.access.location}")
  private String publicAccessLocation;

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    /**
     * Any request that arrives at [endPoint]/public/** will be mapped to
     * /content/public/
     * Note that classpath here is by default src/main/resources.
     * /public/** → /var/ngelmak/storage/**
     */
    String pattern = publicAccessLocation + "/**";

    // location is already absolute: /var/ngelmak/storage
    Path path = Paths.get(location).toAbsolutePath();

    // Spring needs "file:/var/ngelmak/storage/"
    String target = "file:" + path.toString() + "/";

    registry.addResourceHandler(pattern)
        .addResourceLocations(target)
        .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS)
            .cachePrivate()
            .mustRevalidate())
        .resourceChain(true)
        .addResolver(new PathResourceResolver());
  }
}
