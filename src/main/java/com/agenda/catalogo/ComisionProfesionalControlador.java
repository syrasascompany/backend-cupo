package com.agenda.catalogo;

import com.agenda.common.ContextoEmpresa;
import com.agenda.common.NoEncontradoException;
import com.agenda.common.ReglaNegocioException;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

/**
 * El porcentaje que se le paga a cada profesional.
 *
 * Va en su propio controlador para no tocar el catálogo, que ya está
 * probado y funcionando.
 */
@RestController
@RequestMapping("/api/profesionales")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ComisionProfesionalControlador {

    private final ProfesionalRepositorio profesionales;

    public record Comision(@NotNull BigDecimal comisionPct) {}

    @PatchMapping("/{id}/comision")
    @Transactional
    public Profesional cambiar(@PathVariable Long id, @RequestBody Comision datos) {
        Profesional p = profesionales.findById(id)
                .filter(x -> x.getEmpresaId().equals(ContextoEmpresa.actual()))
                .orElseThrow(() -> new NoEncontradoException("Profesional no encontrada"));

        BigDecimal pct = datos.comisionPct();
        if (pct.compareTo(BigDecimal.ZERO) < 0 || pct.compareTo(new BigDecimal("100")) > 0) {
            throw new ReglaNegocioException("El porcentaje va entre 0 y 100");
        }
        p.setComisionPct(pct);
        return p;
    }
}