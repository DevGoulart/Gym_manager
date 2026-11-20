package com.academia.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.NetworkInterface;
import java.util.Collections;

@Component
public class EnderecoServidorLogger {

    private static final Logger log = LoggerFactory.getLogger(EnderecoServidorLogger.class);

    @Value("${server.port:8080}")
    private int porta;

    @EventListener(ApplicationReadyEvent.class)
    public void mostrarEnderecos() {
        StringBuilder sb = new StringBuilder("\n\n========== API DA ACADEMIA NO AR ==========\n");
        sb.append("No emulador Android use: http://10.0.2.2:").append(porta).append('\n');
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                Collections.list(ni.getInetAddresses()).stream()
                        .filter(a -> a instanceof Inet4Address)
                        .forEach(a -> sb.append("No celular (mesmo Wi-Fi) use: http://")
                                .append(a.getHostAddress()).append(':').append(porta)
                                .append("   [").append(ni.getDisplayName()).append("]\n"));
            }
        } catch (Exception e) {
            sb.append("(não foi possível listar os IPs: ").append(e.getMessage()).append(")\n");
        }
        sb.append("===========================================\n");
        log.info(sb.toString());
    }
}
