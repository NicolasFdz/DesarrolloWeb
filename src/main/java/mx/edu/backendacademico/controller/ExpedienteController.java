package mx.edu.backendacademico.controller;

import mx.edu.backendacademico.dto.AlumnoMapper;
import mx.edu.backendacademico.dto.AlumnoResponse;
import mx.edu.backendacademico.exception.AccesoDenegadoException;
import mx.edu.backendacademico.security.AccesoExpediente;
import mx.edu.backendacademico.service.AlumnoService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ExpedienteController {
    private final AlumnoService service;
    public ExpedienteController(AlumnoService service) { this.service = service; }

    // La identidad sale del principal; ningún parámetro de la petición puede sustituirla.
    @GetMapping("/mi-expediente")
    public AlumnoResponse miExpediente(Authentication autenticacion) {
        return AlumnoMapper.toResponse(service.buscarPorMatricula(autenticacion.getName()));
    }

    // Regla por objeto: solo el titular o un administrador leen este expediente.
    @GetMapping("/alumnos/{id}/expediente")
    public AlumnoResponse expediente(@PathVariable Long id, Authentication autenticacion) {
        var alumno = service.buscar(id);
        if (!AccesoExpediente.puedeLeer(autenticacion, alumno)) throw new AccesoDenegadoException();
        return AlumnoMapper.toResponse(alumno);
    }
}