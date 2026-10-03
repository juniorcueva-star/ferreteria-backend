package com.ferreteria.config;

import com.ferreteria.security.JwtProperties;
import com.ferreteria.security.RespuestaErrorSeguridad;
import com.ferreteria.security.UsuarioJwtConverter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.AuthorizeHttpRequestsConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Seguridad de la API: sin sesiones (stateless), autenticacion con token JWT en el header
 * "Authorization: Bearer ..." y permisos por rol en la tabla {@link #permisosPorRol}.
 */
@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    private static final String ADMIN = "ADMIN";
    private static final String VENDEDOR = "VENDEDOR";
    private static final String ALMACENERO = "ALMACENERO";

    private static final String[] RUTAS_PUBLICAS = {
            "/api/auth/login",
            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, UsuarioJwtConverter usuarioJwtConverter,
                                                   RespuestaErrorSeguridad respuestaError) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable) // API con token, no usa cookies
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(SecurityConfig::permisosPorRol)
                .oauth2ResourceServer(oauth -> oauth
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(usuarioJwtConverter))
                        .authenticationEntryPoint(respuestaError.noAutenticado())
                        .accessDeniedHandler(respuestaError.accesoDenegado()))
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(respuestaError.noAutenticado())
                        .accessDeniedHandler(respuestaError.accesoDenegado()));
        return http.build();
    }

    /**
     * Tabla de permisos por rol, en un solo lugar. Se evalua en el filtro de seguridad, ANTES de leer y validar
     * el cuerpo de la peticion: un rol sin permiso recibe 403 aunque envie datos invalidos.
     * El permiso por tienda (que un vendedor solo vea su tienda) se valida despues, en los services.
     * El orden importa: las reglas mas especificas van primero.
     */
    private static void permisosPorRol(
            AuthorizeHttpRequestsConfigurer<HttpSecurity>.AuthorizationManagerRequestMatcherRegistry auth) {
        auth
                // Publico
                .requestMatchers(RUTAS_PUBLICAS).permitAll()
                .requestMatchers(HttpMethod.GET, "/imagenes/**").permitAll()
                .requestMatchers("/error").permitAll()
                // Organizacion
                .requestMatchers("/api/auth/**").authenticated()
                .requestMatchers("/api/empresas/**", "/api/usuarios/**").hasRole(ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/ubicaciones/**").authenticated()
                .requestMatchers("/api/ubicaciones/**").hasRole(ADMIN)
                // Catalogo: todos consultan, solo el ADMIN modifica
                .requestMatchers(HttpMethod.GET, "/api/categorias/**", "/api/productos/**").authenticated()
                .requestMatchers("/api/categorias/**", "/api/productos/**").hasRole(ADMIN)
                // Compras
                .requestMatchers(HttpMethod.POST, "/api/compras/*/anular").hasRole(ADMIN)
                .requestMatchers("/api/compras/**", "/api/proveedores/**").hasAnyRole(ADMIN, ALMACENERO)
                // Inventario y traslados
                .requestMatchers(HttpMethod.POST, "/api/traslados/*/recibir").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/traslados", "/api/traslados/*/anular")
                .hasAnyRole(ADMIN, ALMACENERO)
                .requestMatchers(HttpMethod.GET, "/api/traslados/**").authenticated()
                .requestMatchers(HttpMethod.POST, "/api/inventario/ajustes").hasAnyRole(ADMIN, ALMACENERO)
                .requestMatchers(HttpMethod.POST, "/api/inventario/inicial").hasRole(ADMIN)
                .requestMatchers(HttpMethod.PUT, "/api/inventario/stock/minimo").hasRole(ADMIN)
                .requestMatchers(HttpMethod.GET, "/api/inventario/**").authenticated()
                // Punto de venta
                .requestMatchers("/api/clientes/**", "/api/cajas/**", "/api/ventas/**", "/api/fiado/**")
                .hasAnyRole(ADMIN, VENDEDOR)
                .requestMatchers(HttpMethod.GET, "/api/metodos-pago/**").authenticated()
                // Reportes
                .requestMatchers("/api/reportes/ventas-por-tienda", "/api/reportes/productos-mas-vendidos")
                .hasAnyRole(ADMIN, VENDEDOR)
                .requestMatchers("/api/reportes/traslados-por-tienda", "/api/reportes/compras-por-proveedor")
                .hasAnyRole(ADMIN, ALMACENERO)
                .requestMatchers(HttpMethod.GET, "/api/reportes/stock-bajo").authenticated()
                // Cualquier otra ruta o metodo no listado arriba queda cerrado
                .anyRequest().denyAll();
    }

    /** Origenes del frontend que pueden llamar a la API (variable CORS_ORIGENES). */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.origenes}") List<String> origenes) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origenes);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtEncoder jwtEncoder(JwtProperties properties) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(clave(properties)));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtProperties properties) {
        return NimbusJwtDecoder.withSecretKey(clave(properties)).macAlgorithm(MacAlgorithm.HS256).build();
    }

    private SecretKey clave(JwtProperties properties) {
        return new SecretKeySpec(properties.secreto().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }
}
