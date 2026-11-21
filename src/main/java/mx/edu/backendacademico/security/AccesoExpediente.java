package mx.edu.backendacademico.security;

// Regla de acceso por objeto: decide si esta cuenta puede leer este expediente.

import mx.edu.backendacademico.domain.Alumno;
import org.springframework.security.core.Authentication;

public final class AccesoExpediente {
    private AccesoExpediente() {}

    public static boolean puedeLeer(Authentication autenticacion, Alumno alumno) {
        if (autenticacion == null || !autenticacion.isAuthenticated()) return false;
        boolean admin = tieneRol(autenticacion, "ROLE_ADMIN");
        boolean titular = tieneRol(autenticacion, "ROLE_ALUMNO")
                && autenticacion.getName().equalsIgnoreCase(alumno.matricula());
        return admin || titular;
    }

    private static boolean tieneRol(Authentication autenticacion, String rol) {
        return autenticacion.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals(rol));
    }
}