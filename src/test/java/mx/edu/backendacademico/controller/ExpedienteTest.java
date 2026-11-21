package mx.edu.backendacademico.controller;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import mx.edu.backendacademico.config.SecurityConfig;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.service.AlumnoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ExpedienteController.class)
@Import(SecurityConfig.class)
class ExpedienteTest {
    @Autowired MockMvc mvc;
    @MockitoBean AlumnoService service;
    private static final Alumno DE_ANA = new Alumno(1L, "A001", "Ana", "ana@u.mx", EstatusAlumno.ACTIVO);
    private static final Alumno DE_BETO = new Alumno(2L, "B002", "Beto", "beto@u.mx", EstatusAlumno.ACTIVO);

    // ---- propietario ----
    @Test void titularConsultaSuExpedienteConMiExpediente() throws Exception {
        when(service.buscarPorMatricula("A001")).thenReturn(DE_ANA);
        mvc.perform(get("/api/v1/mi-expediente").with(user("A001").roles("ALUMNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricula").value("A001"));
    }

    @Test void titularConsultaSuExpedientePorId() throws Exception {
        when(service.buscar(1L)).thenReturn(DE_ANA);
        mvc.perform(get("/api/v1/alumnos/1/expediente").with(user("A001").roles("ALUMNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1));
    }

    // ---- ajeno ----
    @Test void usuarioAjenoNoLeeElExpedienteDeOtro() throws Exception {
        when(service.buscar(1L)).thenReturn(DE_ANA);
        mvc.perform(get("/api/v1/alumnos/1/expediente").with(user("B002").roles("ALUMNO")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test void docenteTambienEsAjenoAlExpediente() throws Exception {
        when(service.buscar(1L)).thenReturn(DE_ANA);
        mvc.perform(get("/api/v1/alumnos/1/expediente").with(user("docente").roles("DOCENTE")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("ACCESO_DENEGADO"));
    }

    @Test void miExpedienteIgnoraCualquierIdentidadEnLaPeticion() throws Exception {
        // Beto intenta presentarse como Ana por parámetro: la identidad solo sale del principal.
        when(service.buscarPorMatricula("B002")).thenReturn(DE_BETO);
        mvc.perform(get("/api/v1/mi-expediente?alumnoId=1&usuario=A001").with(user("B002").roles("ALUMNO")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricula").value("B002"));
        verify(service, never()).buscarPorMatricula("A001");
    }

    // ---- administrador ----
    @Test void administradorLeeCualquierExpedientePorId() throws Exception {
        when(service.buscar(1L)).thenReturn(DE_ANA);
        mvc.perform(get("/api/v1/alumnos/1/expediente").with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matricula").value("A001"));
    }

    @Test void administradorNoTieneMiExpedientePropio() throws Exception {
        mvc.perform(get("/api/v1/mi-expediente").with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }
}