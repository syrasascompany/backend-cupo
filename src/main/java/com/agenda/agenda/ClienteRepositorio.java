package com.agenda.agenda;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ClienteRepositorio extends JpaRepository<Cliente, Long> {
    Optional<Cliente> findByEmpresaIdAndTelefono(Long empresaId, String telefono);
    List<Cliente> findByEmpresaIdOrderByNombre(Long empresaId);
}
