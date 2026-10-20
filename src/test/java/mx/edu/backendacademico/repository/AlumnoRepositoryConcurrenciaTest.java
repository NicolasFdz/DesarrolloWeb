package mx.edu.backendacademico.repository;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import mx.edu.backendacademico.domain.Alumno;
import mx.edu.backendacademico.domain.EstatusAlumno;
import mx.edu.backendacademico.exception.MatriculaDuplicadaException;
import org.junit.jupiter.api.Test;

class AlumnoRepositoryConcurrenciaTest {

    @Test
    void dosHilosConLaMismaMatriculaProducenUnRegistroYUnConflicto() throws Exception {
        var repositorio = new AlumnoRepositoryEnMemoria();
        var listos = new CountDownLatch(2);      // ambas tareas ya están en ejecución
        var salida = new CountDownLatch(1);      // señal para arrancar a la vez
        var terminados = new CountDownLatch(2);  // ambas tareas ya concluyeron
        var registros = new AtomicInteger();
        var conflictos = new AtomicInteger();

        Runnable tarea = () -> {
            try {
                listos.countDown();
                salida.await();
                repositorio.guardar(new Alumno(null, "A001", "Ada", "ada@u.mx", EstatusAlumno.ACTIVO));
                registros.incrementAndGet();
            } catch (MatriculaDuplicadaException e) {
                conflictos.incrementAndGet();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                terminados.countDown();
            }
        };

        var pool = Executors.newFixedThreadPool(2);
        try {
            pool.execute(tarea);
            pool.execute(tarea);
            assertTrue(listos.await(5, TimeUnit.SECONDS), "Las tareas no arrancaron");
            salida.countDown();
            assertTrue(terminados.await(5, TimeUnit.SECONDS), "Las tareas no terminaron");
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, registros.get());
        assertEquals(1, conflictos.get());
        assertEquals(1, repositorio.buscarTodos().size());
    }
}