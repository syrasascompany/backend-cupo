package com.agenda.catalogo;

import com.agenda.common.*;
import com.agenda.empresa.EmpresaServicio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CatalogoServicio {

    private final ProfesionalRepositorio profesionales;
    private final ServicioRepositorio servicios;
    private final ServicioProfesionalRepositorio serviciosProfesional;
    private final EmpresaServicio empresaServicio;
    private final com.agenda.agenda.CitaRepositorio citas;

    // ---------------- Profesionales ----------------

    public List<Profesional> listarProfesionales(boolean soloActivos) {
        Long empresaId = ContextoEmpresa.actual();
        return soloActivos
                ? profesionales.findByEmpresaIdAndActivoTrue(empresaId)
                : profesionales.findByEmpresaId(empresaId);
    }

    @Transactional
    public Profesional crearProfesional(String nombre, String telefono, String color) {
        Long empresaId = ContextoEmpresa.actual();
        empresaServicio.verificarCupoDeProfesionales(empresaId);   // respeta el plan contratado

        Profesional p = new Profesional();
        p.setEmpresaId(empresaId);
        p.setNombre(nombre);
        p.setTelefono(telefono);
        p.setColor(color);
        return profesionales.save(p);
    }

    @Transactional
    public Profesional actualizarProfesional(Long id, String nombre, String telefono,
                                             String color, Boolean activo) {
        Profesional p = miProfesional(id);
        if (nombre != null) p.setNombre(nombre);
        if (telefono != null) p.setTelefono(telefono);
        if (color != null) p.setColor(color);
        if (activo != null) p.setActivo(activo);
        return p;
    }

    /**
     * Cuando una profesional se va no se borra: se desactiva. Sus citas
     * pasadas tienen que seguir en el historial y sus clientas también.
     *
     * Deja de aparecer en la agenda y libera un puesto del plan, pero
     * primero hay que resolver las citas que ya tenía agendadas: si se
     * apaga sin más, esas clientas llegan un día y no hay quien las atienda.
     */
    @Transactional
    public void desactivarProfesional(Long id, boolean forzar) {
        Profesional p = miProfesional(id);

        long pendientes = citasFuturas(id);
        if (pendientes > 0 && !forzar) {
            throw new ReglaNegocioException(
                    "Tiene " + pendientes + " citas agendadas. Reasígnelas antes de retirarla.");
        }
        p.setActivo(false);
    }

    /** Cuántas citas confirmadas le quedan de aquí en adelante. */
    public long citasFuturas(Long profesionalId) {
        return citas.contarFuturasDeProfesional(profesionalId, java.time.Instant.now());
    }

    public Profesional miProfesional(Long id) {
        Profesional p = profesionales.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Profesional no encontrada"));
        if (!p.getEmpresaId().equals(ContextoEmpresa.actual()))
            throw new NoEncontradoException("Profesional no encontrada");
        return p;
    }

    // ---------------- Servicios ----------------

    public List<Servicio> listarServicios(boolean soloActivos) {
        Long empresaId = ContextoEmpresa.actual();
        return soloActivos
                ? servicios.findByEmpresaIdAndActivoTrue(empresaId)
                : servicios.findByEmpresaId(empresaId);
    }

    @Transactional
    public Servicio crearServicio(String nombre, Long precioCentavos, Integer duracionMin) {
        Servicio s = new Servicio();
        s.setEmpresaId(ContextoEmpresa.actual());
        s.setNombre(nombre);
        s.setPrecioCentavos(precioCentavos == null ? 0L : precioCentavos);
        s.setDuracionMin(duracionMin == null ? 60 : duracionMin);
        return servicios.save(s);
    }

    @Transactional
    public Servicio actualizarServicio(Long id, String nombre, Long precioCentavos,
                                       Integer duracionMin, Boolean activo) {
        Servicio s = miServicio(id);
        if (nombre != null) s.setNombre(nombre);
        if (precioCentavos != null) s.setPrecioCentavos(precioCentavos);
        if (duracionMin != null) s.setDuracionMin(duracionMin);
        if (activo != null) s.setActivo(activo);
        return s;
    }

    @Transactional
    public void desactivarServicio(Long id) {
        miServicio(id).setActivo(false);
    }

    public Servicio miServicio(Long id) {
        Servicio s = servicios.findById(id)
                .orElseThrow(() -> new NoEncontradoException("Servicio no encontrado"));
        if (!s.getEmpresaId().equals(ContextoEmpresa.actual()))
            throw new NoEncontradoException("Servicio no encontrado");
        return s;
    }

    // ---------------- Quién hace qué y en cuánto tiempo ----------------

    public List<ServicioProfesional> duracionesDeServicio(Long servicioId) {
        miServicio(servicioId);
        return serviciosProfesional.findByServicioId(servicioId);
    }

    public List<ServicioProfesional> duracionesDeProfesional(Long profesionalId) {
        miProfesional(profesionalId);
        return serviciosProfesional.findByProfesionalId(profesionalId);
    }

    /**
     * Deja lista a una profesional en un solo paso: qué servicios hace y,
     * solo donde haga falta, cuánto se demora ella.
     * Lo que no venga en la lista se le quita.
     */
    @Transactional
    public List<ServicioProfesional> definirServiciosDe(Long profesionalId,
                                                        List<AsignacionServicio> lista) {
        miProfesional(profesionalId);

        List<ServicioProfesional> actuales = serviciosProfesional.findByProfesionalId(profesionalId);
        for (ServicioProfesional sp : actuales) {
            boolean sigue = lista.stream().anyMatch(a -> a.servicioId().equals(sp.getServicioId()));
            if (!sigue) serviciosProfesional.delete(sp);
        }

        return lista.stream()
                .map(a -> asignar(a.servicioId(), profesionalId, a.duracionMin()))
                .toList();
    }

    /** duracionMin en nulo significa "se demora lo normal del servicio". */
    public record AsignacionServicio(Long servicioId, Integer duracionMin) {}

    /** Asigna el servicio. Con duración en nulo, usa la normal del servicio. */
    @Transactional
    public ServicioProfesional asignar(Long servicioId, Long profesionalId, Integer duracionMin) {
        miServicio(servicioId);
        miProfesional(profesionalId);
        if (duracionMin != null && duracionMin <= 0)
            throw new ReglaNegocioException("La duración debe ser mayor a cero");

        ServicioProfesional sp = serviciosProfesional.findByServicioId(servicioId).stream()
                .filter(x -> x.getProfesionalId().equals(profesionalId))
                .findFirst()
                .orElseGet(() -> {
                    ServicioProfesional nuevo = new ServicioProfesional();
                    nuevo.setServicioId(servicioId);
                    nuevo.setProfesionalId(profesionalId);
                    return nuevo;
                });
        sp.setDuracionMin(duracionMin);
        return serviciosProfesional.save(sp);
    }

    @Transactional
    public void quitarAsignacion(Long servicioId, Long profesionalId) {
        miServicio(servicioId);
        serviciosProfesional.deleteByServicioIdAndProfesionalId(servicioId, profesionalId);
    }
}
