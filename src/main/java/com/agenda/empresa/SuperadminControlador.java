package com.agenda.empresa;

import com.agenda.empresa.EmpresaDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Tu panel: dar de alta clientes y ajustarles el plan. */
@RestController
@RequestMapping("/api/superadmin/empresas")
@RequiredArgsConstructor
public class SuperadminControlador {

    private final EmpresaServicio servicio;

    @GetMapping
    public List<EmpresaVista> listar() { return servicio.listar(); }

    @PostMapping
    public EmpresaVista crear(@Valid @RequestBody CrearEmpresa datos) {
        return servicio.crear(datos);
    }

    public record CambioPlan(String plan, Integer maxProfesionales) {}

    @PatchMapping("/{id}/plan")
    public EmpresaVista cambiarPlan(@PathVariable Long id, @RequestBody CambioPlan cambio) {
        return servicio.cambiarPlan(id, cambio.plan(), cambio.maxProfesionales());
    }

    @PatchMapping("/{id}/activa")
    public void activar(@PathVariable Long id, @RequestParam boolean valor) {
        servicio.activar(id, valor);
    }
}
