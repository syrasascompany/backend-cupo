package com.agenda.security;

import com.agenda.usuario.Usuario;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Service
public class JwtServicio {

    private final SecretKey clave;
    private final long duracionMs;

    public JwtServicio(@Value("${app.jwt.secreto}") String secreto,
                       @Value("${app.jwt.horas:12}") long horas) {
        this.clave = Keys.hmacShaKeyFor(secreto.getBytes(StandardCharsets.UTF_8));
        this.duracionMs = horas * 60 * 60 * 1000;
    }

    public String generar(Usuario u) {
        var constructor = Jwts.builder()
                .subject(u.getEmail())
                .claim("uid", u.getId())
                .claim("rol", u.getRol().name())
                .claim("nombre", u.getNombre())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + duracionMs));

        if (u.getEmpresaId() != null) constructor.claim("empresaId", u.getEmpresaId());
        if (u.getProfesionalId() != null) constructor.claim("profesionalId", u.getProfesionalId());

        return constructor.signWith(clave).compact();
    }

    public Claims leer(String token) {
        return Jwts.parser().verifyWith(clave).build().parseSignedClaims(token).getPayload();
    }
}
