package br.udq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Optional;

import jakarta.ws.rs.WebApplicationException;

import org.junit.jupiter.api.Test;

class IntentClassifierTest {

    @Test
    void semChaveOAppSobeMasAClassificacaoRespondeServicoIndisponivel() {
        var semChave = new ShapeConfig() {
            public Optional<String> apiKey() {
                return Optional.empty();
            }

            public String baseUrl() {
                return "https://api.openai.com/v1";
            }

            public String model() {
                return "gpt-6-luna";
            }

            public Duration timeout() {
                return Duration.ofSeconds(10);
            }

            public String zone() {
                return "America/Sao_Paulo";
            }
        };
        var error = assertThrows(WebApplicationException.class,
                () -> new IntentClassifier(semChave).classify("25 min de foco", false));
        assertEquals(503, error.getResponse().getStatus());
    }
}