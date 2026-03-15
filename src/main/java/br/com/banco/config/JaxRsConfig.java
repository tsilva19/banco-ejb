package br.com.banco.config;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/*
 * @ApplicationPath → define o caminho base de todas as URLs REST.
 *
 * Com "/api", as URLs ficam:
 *   http://localhost:8080/banco-ejb/api/contas
 *   http://localhost:8080/banco-ejb/api/contas/1/boletos
 *
 * Estendendo Application sem sobrescrever nada, o JAX-RS
 * detecta automaticamente todas as classes com @Path.
 *
 * Equivalente ao @EnableWebMvc + configuração de prefixo do Spring.
 */
@ApplicationPath("/api")
public class JaxRsConfig extends Application {
    // Nenhum código necessário aqui.
    // A anotação @ApplicationPath é suficiente.
}