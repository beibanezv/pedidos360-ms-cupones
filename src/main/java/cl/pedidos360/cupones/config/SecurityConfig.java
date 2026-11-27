package cl.pedidos360.cupones.config;

import com.nimbusds.jwt.JWTParser;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Crear cupones es de administracion; consultarlos y validarlos es de comprador.
 * Entra y Cognito (solo ID token) se validan con issuer y audience exactos; el
 * authorizer del Gateway no basta porque no distingue roles.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  private static final String JWKS_DEFAULT = "https://login.microsoftonline.com/";

  @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri:https://login.microsoftonline.com/}")
  private String issuerUri;

  @Value("${azure.tenant-id:e5372bf0-c5e3-4286-887c-79069f209c1f}")
  private String tenantId;

  @Value("${azure.audience:api://446c57cb-aba2-4b7a-ab01-6f2e6af7d35c}")
  private String azureAudience;

  @Value("${cognito.jwks-uri:https://cognito-idp.us-east-1.amazonaws.com/us-east-1_A8lrPsDta/.well-known/jwks.json}")
  private String cognitoJwksUri;

  @Value("${cognito.issuer:}")
  private String cognitoIssuer;

  @Value("${cognito.client-id:2ba1fjeai3c2i77sb5dpd7j91m}")
  private String cognitoClientId;

  @Value("${cors.allowed-origins:http://localhost:4200}")
  private String allowedOrigins;

  @Bean
  public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
    http
        .csrf(csrf -> csrf.disable())
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .authorizeHttpRequests(auth -> auth
            .requestMatchers(HttpMethod.POST, "/cupones").hasRole("Admin")
            .requestMatchers(HttpMethod.GET, "/cupones/**").hasAnyRole("Cliente", "Admin")
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));

    return http.build();
  }

  @Bean
  public JwtAuthenticationConverter jwtAuthenticationConverter() {
    JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
    converter.setJwtGrantedAuthoritiesConverter(jwt -> {
      List<GrantedAuthority> authorities = new ArrayList<>();
      if ("id".equals(jwt.getClaimAsString("token_use"))) {
        // Todo ID token valido de nuestro pool es comprador. Sus claims "roles"
        // se ignoran a proposito: roles=["Admin"] en Cognito no escala.
        authorities.add(new SimpleGrantedAuthority("ROLE_Cliente"));
      } else {
        // Azure AD roles vienen en "roles": ["Cliente","Admin"].
        List<String> roles = jwt.getClaimAsStringList("roles");
        if (roles != null) {
          for (String rol : roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + rol));
          }
        }
      }
      return authorities;
    });
    return converter;
  }

  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(allowedOrigins.split(",")));
    config.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
    config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
  }

  @Bean
  public JwtDecoder jwtDecoder() {
    String tenant = obligatorio(tenantId, "AZURE_TENANT_ID");
    String audienciaAzure = obligatorio(azureAudience, "AZURE_AUDIENCE");
    String jwksCognito = obligatorio(cognitoJwksUri, "COGNITO_JWKS_URI");
    String clienteCognito = obligatorio(cognitoClientId, "COGNITO_CLIENT_ID");
    String emisorCognito = (cognitoIssuer == null || cognitoIssuer.isBlank())
        ? issuerDesdeJwks(jwksCognito) : cognitoIssuer.trim();
    String emisorAzure = "https://sts.windows.net/" + tenant + "/";

    String base = (issuerUri == null || issuerUri.isBlank()) ? JWKS_DEFAULT : issuerUri.trim();
    if (!base.endsWith("/")) {
      base += "/";
    }
    // Los JWKS se descargan solo al decodificar el primer token de cada emisor.
    Map<String, JwtDecoder> decoders = Map.of(
        emisorAzure, decoder(emisorAzure, base + tenant + "/discovery/keys", audienciaAzure, false),
        emisorCognito, decoder(emisorCognito, jwksCognito, clienteCognito, true));

    return token -> {
      try {
        // El iss sin verificar solo elige entre dos decoders preconfigurados.
        String iss = JWTParser.parse(token).getJWTClaimsSet().getIssuer();
        JwtDecoder elegido = iss == null ? null : decoders.get(iss);
        if (elegido == null) throw new BadJwtException("Emisor no permitido");
        return elegido.decode(token);
      } catch (ParseException e) {
        throw new BadJwtException("JWT malformado", e);
      }
    };
  }

  private static String obligatorio(String valor, String nombre) {
    if (valor == null || valor.isBlank()) {
      throw new IllegalStateException("Configuración JWT obligatoria vacía: " + nombre);
    }
    return valor.trim();
  }

  private static String issuerDesdeJwks(String jwks) {
    String sufijo = "/.well-known/jwks.json";
    if (!jwks.endsWith(sufijo)) {
      throw new IllegalStateException("COGNITO_ISSUER requerido: COGNITO_JWKS_URI no termina en " + sufijo);
    }
    return jwks.substring(0, jwks.length() - sufijo.length());
  }

  private static JwtDecoder decoder(String issuer, String jwks, String audience, boolean cognito) {
    NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwks).build();
    decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
        JwtValidators.createDefaultWithIssuer(issuer),
        new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(audience)),
        new JwtClaimValidator<Object>("exp", exp -> exp != null),
        new JwtClaimValidator<String>("token_use", use -> !cognito || "id".equals(use))));
    return decoder;
  }
}
