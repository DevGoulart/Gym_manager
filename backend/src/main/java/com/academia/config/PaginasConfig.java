package com.academia.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Endereços amigáveis para as páginas do site (sem o ".html"). */
@Configuration
public class PaginasConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registro) {
        // Página de apresentação do produto: http://localhost:8080/apresentacao
        registro.addViewController("/apresentacao").setViewName("forward:/apresentacao.html");
    }
}
