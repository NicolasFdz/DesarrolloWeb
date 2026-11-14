package mx.edu.backendacademico.service;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import mx.edu.backendacademico.exception.OperacionAcademicaException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// Prueba de laboratorio contra PostgreSQL real (Docker Compose). Se omite si no se pide.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
class InscripcionConcurrenciaPostgresTest {
    @Autowired InscripcionService inscripciones;
    @Autowired AlumnoService alumnos;
    @Autowired DataSource dataSource;

    @Test
    void dosAdmisionesSimultaneasPersistenUnaYRechazanLaOtraPorCupo() throws Exception {
        String sufijo = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        long materiaId = crearMateriaConUnLugar("LAB" + sufijo);
        var ada = alumnos.crear("LA" + sufijo, "Ada Lovelace", "ada" + sufijo + "@universidad.mx");
        var alan = alumnos.crear("LB" + sufijo, "Alan Turing", "alan" + sufijo + "@universidad.mx");

        var resultados = new ConcurrentLinkedQueue<String>();
        var listos = new CountDownLatch(2);      // ambos hilos preparados
        var salida = new CountDownLatch(1);      // arranque simultáneo
        var terminados = new CountDownLatch(2);  // ambos hilos concluyeron
        var pool = Executors.newFixedThreadPool(2);
        try (Connection titular = dataSource.getConnection()) {
            titular.setAutoCommit(false);
            int pidTitular = bloquearFilaDeMateria(titular, materiaId);

            for (Long alumnoId : List.of(ada.id(), alan.id())) {
                pool.execute(() -> {
                    try {
                        listos.countDown();
                        salida.await();
                        inscripciones.inscribir(alumnoId, materiaId);   // bean real
                        resultados.add("INSCRITO");
                    } catch (OperacionAcademicaException e) {
                        resultados.add(e.getCodigo());
                    } catch (Exception e) {
                        resultados.add("ERROR " + e);
                    } finally {
                        terminados.countDown();
                    }
                });
            }
            assertTrue(listos.await(10, TimeUnit.SECONDS), "Los hilos no se prepararon");
            salida.countDown();

            // Evidencia: ambas sesiones esperan un bloqueo DENTRO de PostgreSQL.
            System.out.println("pid del titular del bloqueo: " + pidTitular);
            esperarSesionesBloqueadas(2).forEach(System.out::println);

            titular.commit();   // libera la fila y deja avanzar a los hilos
            assertTrue(terminados.await(15, TimeUnit.SECONDS), "Los hilos no terminaron");
        } finally {
            pool.shutdownNow();
            limpiar(materiaId, ada.id(), alan.id());
        }

        assertEquals(List.of("INSCRITO", "SIN_CUPO"), resultados.stream().sorted().toList());
    }

    private long crearMateriaConUnLugar(String clave) throws Exception {
        try (Connection c = dataSource.getConnection();
             var ps = c.prepareStatement("INSERT INTO materias(clave, nombre, creditos, cupo) "
                     + "VALUES (?, 'Materia de laboratorio', 1, 1) RETURNING id")) {
            ps.setString(1, clave);
            try (ResultSet rs = ps.executeQuery()) { rs.next(); return rs.getLong(1); }
        }
    }

    private int bloquearFilaDeMateria(Connection titular, long materiaId) throws Exception {
        int pid;
        try (var st = titular.createStatement(); var rs = st.executeQuery("SELECT pg_backend_pid()")) {
            rs.next(); pid = rs.getInt(1);
        }
        try (var ps = titular.prepareStatement("SELECT id FROM materias WHERE id = ? FOR UPDATE")) {
            ps.setLong(1, materiaId);
            ps.executeQuery().close();
        }
        return pid;
    }

    // Consulta el estado observable de PostgreSQL; el plazo es solo una salvaguarda.
    private List<String> esperarSesionesBloqueadas(int esperadas) throws Exception {
        String sql = """
                SELECT pid, state, wait_event_type, wait_event, pg_blocking_pids(pid)::text, left(query, 60)
                FROM pg_stat_activity
                WHERE datname = current_database() AND wait_event_type = 'Lock' AND pid <> pg_backend_pid()
                ORDER BY pid""";
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        try (Connection c = dataSource.getConnection(); var st = c.createStatement()) {
            while (System.nanoTime() < limite) {
                var filas = new ArrayList<String>();
                try (ResultSet rs = st.executeQuery(sql)) {
                    while (rs.next()) {
                        filas.add("pid=" + rs.getInt(1) + " estado=" + rs.getString(2)
                                + " espera=" + rs.getString(3) + "/" + rs.getString(4)
                                + " bloqueada_por=" + rs.getString(5) + " sql=" + rs.getString(6));
                    }
                }
                if (filas.size() == esperadas) return filas;
                Thread.sleep(50);
            }
        }
        fail("No se observaron " + esperadas + " sesiones esperando un bloqueo en PostgreSQL");
        return List.of();
    }

    private void limpiar(long materiaId, Long... alumnoIds) throws Exception {
        try (Connection c = dataSource.getConnection()) {
            ejecutar(c, "DELETE FROM inscripciones WHERE materia_id = ?", materiaId);
            ejecutar(c, "DELETE FROM materias WHERE id = ?", materiaId);
            for (Long id : alumnoIds) ejecutar(c, "DELETE FROM alumnos WHERE id = ?", id);
        }
    }

    private void ejecutar(Connection c, String sql, long valor) throws Exception {
        try (var ps = c.prepareStatement(sql)) { ps.setLong(1, valor); ps.executeUpdate(); }
    }
}