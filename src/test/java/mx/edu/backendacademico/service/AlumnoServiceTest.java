package mx.edu.backendacademico.service;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.repository.AlumnoRepositoryEnMemoria;
import org.junit.jupiter.api.Test;

class AlumnoServiceTest {
    @Test void filtrarPorEstatusNoModificaElRepositorio() {
        var repositorio = new AlumnoRepositoryEnMemoria();
        var servicio = new AlumnoService(repositorio);
        var ada = servicio.crear("A001", "Ada", "ada@u.mx");
        var alan = servicio.crear("A002", "Alan", "alan@u.mx");
        repositorio.guardar(alan.darDeBaja());   // la API aún no tiene endpoint de baja
        var antes = repositorio.buscarTodos();

        assertEquals(List.of(ada), servicio.listar(EstatusAlumno.ACTIVO));
        assertEquals(List.of(alan.darDeBaja()), servicio.listar(EstatusAlumno.BAJA));
        assertEquals(antes, servicio.listar(null));        // sin filtro: mismo contrato
        assertEquals(antes, repositorio.buscarTodos());    // consultar no cambió nada
    }
}