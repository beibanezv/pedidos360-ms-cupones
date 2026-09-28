package cl.pedidos360.cupones.cupon;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CuponController.class)
class CuponControllerTest {

    @Autowired
    MockMvc mvc;

    @MockBean
    CuponRepository repository;

    @Test
    void crear_cuponValido_retorna201() throws Exception {
        when(repository.findByCodigo("BIENVENIDA10")).thenReturn(Optional.empty());
        when(repository.save(any())).thenAnswer(i -> i.getArgument(0));

        mvc.perform(post("/cupones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"bienvenida10\",\"descuentoPct\":10,\"usosMaximos\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.codigo").value("BIENVENIDA10"));
    }

    @Test
    void crear_codigoDuplicado_retorna409() throws Exception {
        when(repository.findByCodigo("DUP"))
                .thenReturn(Optional.of(new Cupon("DUP", 5, 10, null)));

        mvc.perform(post("/cupones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"DUP\",\"descuentoPct\":5,\"usosMaximos\":10}"))
                .andExpect(status().isConflict());
    }

    @Test
    void crear_codigoInvalido_retorna400() throws Exception {
        mvc.perform(post("/cupones")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"codigo\":\"mal codigo!\",\"descuentoPct\":200,\"usosMaximos\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.campos").isMap());
    }

    @Test
    void validar_cuponInexistente_retorna404() throws Exception {
        when(repository.findByCodigo("NADA")).thenReturn(Optional.empty());

        mvc.perform(get("/cupones/NADA/validar"))
                .andExpect(status().isNotFound());
    }

    @Test
    void validar_cuponValido_retornaValidoTrue() throws Exception {
        when(repository.findByCodigo("OK10"))
                .thenReturn(Optional.of(new Cupon("OK10", 10, 5, null)));

        mvc.perform(get("/cupones/OK10/validar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valido").value(true))
                .andExpect(jsonPath("$.descuentoPct").value(10));
    }
}
