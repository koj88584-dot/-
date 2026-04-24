package com.hmdp.config;
import com.hmdp.interceptor.LoginInterceptor;
import com.hmdp.interceptor.RefreshTokenInterceptor;
import com.hmdp.utils.SystemConstants;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;

/**
 * mvc配置
 *
 * @author CHEN
 * @date 2022/10/07
 */
@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登陆拦截器
        registry
                .addInterceptor(new LoginInterceptor())
                .excludePathPatterns("/user/code"
                        , "/user/login"
                        , "/user/register"
                        , "/auth/login"
                        , "/assistant/**"
                        , "/blog/hot"
                        , "/blog/{id}"
                        , "/shop/**"
                        , "/shop-type/**"
                        , "/upload/**"
                        , "/voucher/**"
                        , "/search/**"
                        , "/blog-comments/list/**"
                        , "/blog-comments/replies/**"
                        , "/css/**"
                        , "/js/**"
                        , "/pages/**"
                        , "/pages/css/**"
                        , "/pages/js/**"
                        , "/map/**"
                        , "/imgs/**"
                )
                .order(1);
        //Token续命拦截器 - 排除OPTIONS请求（CORS预检请求）
        registry
                .addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")
                .excludePathPatterns("/error")
                .order(0);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Collections.singletonList("*"));
        config.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(Collections.singletonList("*"));
        config.setExposedHeaders(Collections.singletonList("*"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        FilterRegistrationBean<CorsFilter> bean = new FilterRegistrationBean<>(new CorsFilter(source));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return bean;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String frontendBasePath = System.getProperty("user.dir") + "/hmdp-frontend/";
        String uploadImagePath = Paths.get(SystemConstants.IMAGE_UPLOAD_DIR).toUri().toString();
        String staticImagePath = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "static", "imgs").toUri().toString();
        String staticUploadPath = Paths.get(System.getProperty("user.dir"), "src", "main", "resources", "static", "upload").toUri().toString();

        // 前端页面（/pages/**）
        registry.addResourceHandler("/pages/**")
                .addResourceLocations("file:" + frontendBasePath + "pages/");

        // 前端资源（兼容相对路径与绝对路径）
        registry.addResourceHandler("/css/**")
                .addResourceLocations("file:" + frontendBasePath + "css/");
        registry.addResourceHandler("/js/**")
                .addResourceLocations("file:" + frontendBasePath + "js/");
        registry.addResourceHandler("/pages/css/**")
                .addResourceLocations("file:" + frontendBasePath + "css/");
        registry.addResourceHandler("/pages/js/**")
                .addResourceLocations("file:" + frontendBasePath + "js/");

        registry.addResourceHandler("/imgs/**")
                .addResourceLocations(uploadImagePath, staticImagePath);
        registry.addResourceHandler("/upload/**")
                .addResourceLocations(staticUploadPath);
    }
}
