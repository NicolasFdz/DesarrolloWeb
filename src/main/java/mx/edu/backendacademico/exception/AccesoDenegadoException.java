package mx.edu.backendacademico.exception;

// Un usuario autenticado intentó leer un objeto que no le pertenece.

public class AccesoDenegadoException extends RuntimeException {
    public AccesoDenegadoException() {
        super("No tienes acceso a este expediente");
    }
}