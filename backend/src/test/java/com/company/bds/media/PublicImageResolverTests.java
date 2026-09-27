package com.company.bds.media;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublicImageResolverTests {
    private final PublicImageResolver resolver = new UrlOnlyPublicImageResolver();

    @Test
    void urlOnlyResolverKeepsOrderSkipsBlanksAndHasNoVariants() {
        List<ImageDto> images = resolver.resolve(Arrays.asList(
                "/api/v1/public/media/8f0c4b8e-1111-4a5b-9c3d-000000000001.jpg", null, " ",
                "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00"));

        assertThat(images).extracting(ImageDto::url).containsExactly(
                "/api/v1/public/media/8f0c4b8e-1111-4a5b-9c3d-000000000001.jpg",
                "https://images.unsplash.com/photo-1545324418-cc1a3fa10c00");
        assertThat(images).allSatisfy(image -> {
            assertThat(image.srcset()).isEmpty();
            assertThat(image.width()).isNull();
            assertThat(image.height()).isNull();
            assertThat(image.placeholder()).isNull();
        });
        assertThat(resolver.resolve(List.of())).isEmpty();
        assertThat(resolver.resolveOne("  ")).isEmpty();
        assertThat(resolver.resolveOne("/api/v1/public/media/a.jpg")).contains(ImageDto.urlOnly("/api/v1/public/media/a.jpg"));
    }

    @Test
    void serializesToTheContractShape() throws Exception {
        JsonNode urlOnly = new ObjectMapper().valueToTree(ImageDto.urlOnly("/api/v1/public/media/k.jpg"));
        assertThat(urlOnly.get("url").asText()).isEqualTo("/api/v1/public/media/k.jpg");
        assertThat(urlOnly.get("srcset").isArray()).isTrue();
        assertThat(urlOnly.get("srcset")).isEmpty();
        assertThat(urlOnly.get("width").isNull()).isTrue();

        JsonNode variants = new ObjectMapper().valueToTree(new ImageDto("/api/v1/public/media/k.jpg", 1600, 1000,
                List.of(new ImageDto.Source("/api/v1/public/media/k__w320.webp", 320)), new ImageDto.Placeholder("#c8b8a0")));
        assertThat(variants.get("srcset").get(0).get("width").asInt()).isEqualTo(320);
        assertThat(variants.get("placeholder").get("dominantColor").asText()).isEqualTo("#c8b8a0");
    }

    @Test
    void anotherResolverBeanReplacesTheUrlOnlyFallback() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(UrlOnlyPublicImageResolver.class);
            context.registerBean("variantAware", PublicImageResolver.class, () -> urls -> List.of());
            context.refresh();
            assertThat(context.getBean(PublicImageResolver.class)).isNotInstanceOf(UrlOnlyPublicImageResolver.class);
        }
        try (var context = new AnnotationConfigApplicationContext(UrlOnlyPublicImageResolver.class)) {
            assertThat(context.getBean(PublicImageResolver.class)).isInstanceOf(UrlOnlyPublicImageResolver.class);
        }
    }
}
