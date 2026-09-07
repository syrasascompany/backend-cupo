package com.agenda.notificaciones;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class CorreoServicio {

    private final JavaMailSender remitente;

    @Value("${app.correo.desde}")
    private String desde;

    /**
     * Se envía en segundo plano: si el servidor de correo se demora,
     * la persona que agendó no se queda esperando.
     */
    @Async
    public void enviar(String para, String asunto, String cuerpo) {
        try {
            SimpleMailMessage mensaje = new SimpleMailMessage();
            mensaje.setFrom(desde);
            mensaje.setTo(para);
            mensaje.setSubject(asunto);
            mensaje.setText(cuerpo);
            remitente.send(mensaje);
        } catch (Exception e) {
            // Que falle un correo nunca debe tumbar una operación de agenda.
            log.error("No se pudo enviar el correo a {}: {}", para, e.getMessage());
        }
    }

    public void bienvenidaAdmin(String para, String nombreEmpresa, String email) {
        enviar(para, "Su agenda de " + nombreEmpresa + " está lista", """
                Hola,

                Ya puede entrar al panel de %s con este correo: %s

                Cualquier cosa, respóndanos por aquí.
                """.formatted(nombreEmpresa, email));
    }
}
