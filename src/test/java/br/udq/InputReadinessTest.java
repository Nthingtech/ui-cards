package br.udq;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class InputReadinessTest {
    private final InputReadiness readiness = new InputReadiness();

    @Test
    void esperaQuandoFaltaConteudoOuODetalheFicouPelaMetade() {
        for (String text : new String[] { "me lembra", "jantar com", "dividir", "comprar", "criar um", "eu quero ir para",
                "por favor, me lembra de", "Almoço com o Ben às", "Tarefas: testar,", "Dividir 120 entre", "Reunião às 10:",
                "reunião amanhã às 8" }) {
            assertNotNull(readiness.waitingReason(text), text);
        }
    }

    @Test
    void esperaPeloValorEPessoasNaDivisao() {
        assertNotNull(readiness.waitingReason("Dividir 12"));
        assertNotNull(readiness.waitingReason("Dividir 50 entre 0"));
        assertNotNull(readiness.waitingReason("Rachar 30 entre 2,5"));
        assertNull(readiness.waitingReason("Dividir 120,50 entre 3"));
        assertNull(readiness.waitingReason("Rachar a conta de R$ 90 por 4 pessoas"));
    }

    @Test
    void aceitaTextoCompleto() {
        for (String text : new String[] { "Me lembra de pagar o aluguel", "Jantar com a Priya", "Almoço com o Ben às 10h",
                "25 min de foco", "Comprar leite e ovos", "Hoje foi um bom dia", "O que eu preciso comprar para?", "Oi",
                "Jantar sexta às 8 da noite", "As melhores ideias aparecem durante uma caminhada" }) {
            assertNull(readiness.waitingReason(text), text);
        }
    }
}