package com.agenda.agenda;

import com.agenda.catalogo.ServicioProfesional;
import com.agenda.common.*;
import com.agenda.disponibilidad.DisponibilidadServicio;
import com.agenda.espera.EstadoEspera;
import com.agenda.notificaciones.RecordatorioTarea;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import com.agenda.catalogo.ServicioRepositorio;
import com.agenda.catalogo.ServicioProfesionalRepositorio;

@Service
@RequiredArgsConstructor
public class CitaServicio {

    private final CitaRepositorio citas;
    private final ClienteRepositorio clientes;
    private final ServicioProfesionalRepositorio serviciosProfesional;
    private final ServicioRepositorio servicios;
    private final DisponibilidadServicio disponibilidad;
    private final RecordatorioTarea avisos;
    private final com.agenda.espera.ListaEsperaRepositorio listaEspera;

    /**
     * Crea la cita después de comprobar que el cupo existe de verdad.
     * Si dos personas piden el mismo cupo a la vez, la restricción de la
     * base de datos rechaza la segunda: no hay forma de duplicar.
     */
    @Transactional
    public Cita crear(Long servicioId, Long profesionalId, LocalDateTime inicio,
                      String nombreCliente, String telefonoCliente,
                      OrigenCita origen, String notas) {

        Long empresaId = ContextoEmpresa.actual();
        ZoneId zona = disponibilidad.zonaDeEmpresa(empresaId);

        ServicioProfesional sp = serviciosProfesional.findByServicioId(servicioId).stream()
                .filter(x -> x.getProfesionalId().equals(profesionalId))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException(
                        "Esa profesional no presta ese servicio"));

        // Ajuste propio, o la duración normal del servicio
        int duracion = sp.getDuracionMin() != null ? sp.getDuracionMin()
                : servicios.findById(servicioId)
                    .orElseThrow(() -> new NoEncontradoException("Servicio no encontrado"))
                    .getDuracionMin();

        if (!disponibilidad.estaLibre(servicioId, profesionalId, inicio)) {
            throw new ReglaNegocioException("Ese horario ya no está disponible");
        }

        Cliente cliente = null;
        if (telefonoCliente != null && !telefonoCliente.isBlank()) {
            cliente = clientes.findByEmpresaIdAndTelefono(empresaId, telefonoCliente)
                    .orElseGet(() -> {
                        Cliente nuevo = new Cliente();
                        nuevo.setEmpresaId(empresaId);
                        nuevo.setNombre(nombreCliente == null ? "Sin nombre" : nombreCliente);
                        nuevo.setTelefono(telefonoCliente);
                        return clientes.save(nuevo);
                    });
        }

        Cita cita = new Cita();
        cita.setEmpresaId(empresaId);
        cita.setProfesionalId(profesionalId);
        cita.setServicioId(servicioId);
        cita.setClienteId(cliente == null ? null : cliente.getId());
        cita.setInicio(inicio.atZone(zona).toInstant());
        cita.setFin(inicio.plusMinutes(duracion).atZone(zona).toInstant());
        cita.setOrigen(origen);
        cita.setNotas(notas);
        cita.setEstado(EstadoCita.CONFIRMADA);
        Cita guardada = citas.save(cita);

        // Si esta clienta estaba esperando un cupo y acaba de tomar uno,
        // se marca. De ahí sale el número de cupos recuperados del reporte.
        if (telefonoCliente != null && !telefonoCliente.isBlank()) {
            listaEspera.findByEmpresaIdAndTelefonoAndEstado(
                    empresaId, telefonoCliente, EstadoEspera.AVISADO)
                .forEach(e -> {
                    e.setEstado(EstadoEspera.TOMO_CUPO);
                    listaEspera.save(e);
                });
        }

        return guardada;
    }

    @Transactional
    public Cita reprogramar(Long citaId, Long profesionalId, LocalDateTime nuevoInicio) {
        Long empresaId = ContextoEmpresa.actual();
        Cita cita = citas.findByIdAndEmpresaId(citaId, empresaId)
                .orElseThrow(() -> new NoEncontradoException("Cita no encontrada"));

        // Se libera antes de recalcular, para que su propio espacio cuente como libre.
        cita.setEstado(EstadoCita.CANCELADA);
        citas.saveAndFlush(cita);

        return crear(cita.getServicioId(), profesionalId, nuevoInicio,
                null, null, cita.getOrigen(), cita.getNotas());
    }

    @Transactional
    public void cambiarEstado(Long citaId, EstadoCita nuevo, Long profesionalIdSiTrabajadora) {
        Long empresaId = ContextoEmpresa.actual();
        Cita cita = citas.findByIdAndEmpresaId(citaId, empresaId)
                .orElseThrow(() -> new NoEncontradoException("Cita no encontrada"));

        // Una trabajadora solo puede tocar sus propias citas.
        if (profesionalIdSiTrabajadora != null
                && !cita.getProfesionalId().equals(profesionalIdSiTrabajadora)) {
            throw new ReglaNegocioException("Esa cita no le pertenece");
        }
        EstadoCita anterior = cita.getEstado();
        cita.setEstado(nuevo);
        citas.save(cita);

        // Se libera un cupo: se le ofrece a quien esté en lista de espera.
        if (nuevo == EstadoCita.CANCELADA && anterior != EstadoCita.CANCELADA) {
            avisos.avisarCupoLiberado(cita);
        }
    }

    /** La agenda del día, con el nombre de cada clienta resuelto. */
    public List<CitaVista> vistaDelDia(LocalDate fecha, Long profesionalId) {
        List<Cita> citas = delDia(fecha, profesionalId);

        // Se buscan los clientes de una vez, no uno por cita
        Map<Long, Cliente> porId = clientes.findAllById(
                citas.stream().map(Cita::getClienteId).filter(Objects::nonNull).toList())
            .stream().collect(Collectors.toMap(Cliente::getId, c -> c));

        return citas.stream().map(c -> {
            Cliente cl = c.getClienteId() == null ? null : porId.get(c.getClienteId());
            return new CitaVista(
                    c.getId(), c.getProfesionalId(), c.getServicioId(),
                    c.getInicio(), c.getFin(), c.getEstado(), c.getOrigen(),
                    cl == null ? null : cl.getNombre(),
                    cl == null ? null : cl.getTelefono(),
                    c.getNotas());
        }).toList();
    }

    public List<Cita> delDia(LocalDate fecha, Long profesionalId) {
        Long empresaId = ContextoEmpresa.actual();
        ZoneId zona = disponibilidad.zonaDeEmpresa(empresaId);
        Instant desde = fecha.atStartOfDay(zona).toInstant();
        Instant hasta = fecha.plusDays(1).atStartOfDay(zona).toInstant();

        if (profesionalId != null) {
            return citas.findByEmpresaIdAndProfesionalIdAndInicioBetweenOrderByInicio(
                    empresaId, profesionalId, desde, hasta);
        }
        return citas.vivasEnRango(empresaId, desde, hasta);
    }
}
