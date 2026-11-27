package cl.pedidos360.cupones.cupon;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.pedidos360.cupones.config.SecurityConfig;

@WebMvcTest(CuponController.class)
@Import(SecurityConfig.class)
class CuponControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    CuponRepository repository;

    @MockBean
    JwtDecoder jwtDecoder;

    private static MockHttpServletRequestBuilder conAdmin(MockHttpServletRequestBuilder peticion) {
        return peticion.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin")));
    }

    private static MockHttpServletRequestBuilder conCliente(MockHttpServletRequestBuilder peticion) {
        return peticion.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Cliente")));
    }

    @Test
    void crear_cuponValido_retorna201() throws Exception {
        when(repository.findByCodigo("BIENVENIDA10")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        mvc.perform(conAdmin(post("/cupones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"bienvenida10\",\"descuentoPct\":10,\"usosMaximos\":100}")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("BIENVENIDA10"));
    }

    @Test
    void crear_codigoDuplicado_retorna409() throws Exception {
        when(repository.findByCodigo("DUP"))
                .thenReturn(Optional.of(new Cupon("DUP", 5, 10, null)));

        mvc.perform(conAdmin(post("/cupones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"DUP\",\"descuentoPct\":5,\"usosMaximos\":10}")))
                .andExpect(status().isConflict());
    }

    @Test
    void crear_codigoInvalido_retorna400() throws Exception {
        mvc.perform(conAdmin(post("/cupones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"mal codigo!\",\"descuentoPct\":200,\"usosMaximos\":0}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos").isMap());
    }

    @Test
    void validar_cuponInexistente_retorna404() throws Exception {
        when(repository.findByCodigo("NADA")).thenReturn(Optional.empty());

        mvc.perform(conCliente(get("/cupones/NADA/validar")))
                .andExpect(status().isNotFound());
    }

    @Test
    void validar_cuponValido_retornaValidoTrue() throws Exception {
        when(repository.findByCodigo("OK10"))
                .thenReturn(Optional.of(new Cupon("OK10", 10, 5, null)));

        mvc.perform(conCliente(get("/cupones/OK10/validar")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true))
                .andExpect(jsonPath("$.descuentoPct").value(10));
    }

    @Test
    void crear_sinJwt_retorna401() throws Exception {
        when(repository.findByCodigo("BIENVENIDA10")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        mvc.perform(post("/cupones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"bienvenida10\",\"descuentoPct\":10,\"usosMaximos\":100}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(repository);
    }

    @Test
    void ver_sinJwt_retorna401() throws Exception {
        when(repository.findByCodigo("OK10"))
                .thenReturn(Optional.of(new Cupon("OK10", 10, 5, null)));

        mvc.perform(get("/cupones/OK10"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(repository);
    }

    @Test
    void crear_conCliente_retorna403() throws Exception {
        mvc.perform(conCliente(post("/cupones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"bienvenida10\",\"descuentoPct\":10,\"usosMaximos\":100}")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(repository);
    }

    @Test
    void crear_cognitoConClaimAdmin_retorna403() throws Exception {
        // El converter de SecurityConfig ignora "roles" en tokens de Cognito
        // (ver SecurityConfigTest); aqui el token solo aporta ROLE_Cliente.
        mvc.perform(post("/cupones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"bienvenida10\",\"descuentoPct\":10,\"usosMaximos\":100}")
                        .with(jwt()
                                .jwt(b -> b.claim("token_use", "id").claim("roles", List.of("Admin")))
                                .authorities(new SimpleGrantedAuthority("ROLE_Cliente"))))
                .andExpect(status().isForbidden());

        verifyNoInteractions(repository);
    }

    @Test
    void ver_conCliente_retorna200() throws Exception {
        when(repository.findByCodigo("OK10"))
                .thenReturn(Optional.of(new Cupon("OK10", 10, 5, null)));

        mvc.perform(conCliente(get("/cupones/OK10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("OK10"));
    }

    @Test
    void ver_conAdmin_retorna200() throws Exception {
        when(repository.findByCodigo("OK10"))
                .thenReturn(Optional.of(new Cupon("OK10", 10, 5, null)));

        mvc.perform(conAdmin(get("/cupones/OK10")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.codigo").value("OK10"));
    }

    @Test
    void validar_conCognito_retorna200() throws Exception {
        when(repository.findByCodigo("OK10"))
                .thenReturn(Optional.of(new Cupon("OK10", 10, 5, null)));

        mvc.perform(get("/cupones/OK10/validar")
                        .with(jwt()
                                .jwt(b -> b.claim("token_use", "id").claim("email", "cliente@pedidos360.cl"))
                                .authorities(new SimpleGrantedAuthority("ROLE_Cliente"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true));
    }

    @Test
    void jwtInvalido_retorna401() throws Exception {
        when(jwtDecoder.decode("invalido")).thenThrow(new BadJwtException("token inválido"));

        mvc.perform(get("/cupones/OK10").header(HttpHeaders.AUTHORIZATION, "Bearer invalido"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(repository);
    }

    @Test
    void preflight_origenPermitido_retornaOkConOrigenExacto() throws Exception {
        mvc.perform(options("/cupones")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"));

        verifyNoInteractions(repository);
    }

    @Test
    void preflight_origenAjeno_retorna403SinOrigen() throws Exception {
        mvc.perform(options("/cupones")
                        .header(HttpHeaders.ORIGIN, "http://atacante.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));

        verifyNoInteractions(repository);
    }
}
