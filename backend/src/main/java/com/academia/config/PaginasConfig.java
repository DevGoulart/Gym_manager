package com.academia.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Endereços das páginas do site. */
@Configuration
public class PaginasConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registro) {
        // Entrada padrão: a página que apresenta o produto (http://localhost:8080/)
        registro.addViewController("/").setViewName("forward:/apresentacao.html");
        // O sistema (login e módulos) fica em http://localhost:8080/app
        registro.addViewController("/app").setViewName("forward:/index.html");
        registro.addViewController("/app/").setViewName("redirect:/app");
        // Endereço antigo da apresentação continua funcionando
        registro.addViewController("/apresentacao").setViewName("redirect:/");
    }
}
