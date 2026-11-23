package br.udq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;

class CardParserTest {
    // Relógio parado: sábado, 21/11/2026, 10h em São Paulo. Assim "amanhã" é sempre o mesmo dia.
    private final CardParser parser = new CardParser(
            Clock.fixed(Instant.parse("2026-11-21T13:00:00Z"), ZoneId.of("America/Sao_Paulo")));

    @Test
    void extraiDataEHoraSemIa() {
        var card = parser.parse(Intent.EVENT, "Jantar com a Priya amanhã às 20h");
        assertEquals("2026-11-22", card.fields().get("date"));
        assertEquals("20:00", card.fields().get("time"));
        assertTrue(card.warnings().isEmpty());

        var almoco = parser.parse(Intent.EVENT, "Almoço sexta ao meio-dia").fields();
        assertEquals("2026-11-27", almoco.get("date"));
        assertEquals("12:00", almoco.get("time"));

        assertEquals("15:00", parser.parse(Intent.EVENT, "Reunião terça 3 da tarde").fields().get("time"));
        assertEquals("2026-12-25", parser.parse(Intent.EVENT, "Festa 25/12 20h30").fields().get("date"));
        // Sem ano e já passou: entende que é no ano que vem
        assertEquals("2027-01-05", parser.parse(Intent.EVENT, "Viagem 05/01 8h").fields().get("date"));
    }

    @Test
    void pedeConfirmacaoQuandoFaltaOuEInvalido() {
        assertFalse(parser.parse(Intent.REMINDER, "Me lembra de pagar o aluguel").warnings().isEmpty());
        assertFalse(parser.parse(Intent.EVENT, "Reunião 30/02 às 25h").warnings().isEmpty());
    }

    @Test
    void separaItensDaLista() {
        assertEquals(List.of("leite", "ovos", "pão", "café"),
                parser.parse(Intent.CHECKLIST, "Comprar leite, ovos, pão e café").fields().get("items"));
        // Com vírgulas, só o último "e" separa: "arroz e feijão" continua junto
        assertEquals(List.of("pão", "arroz e feijão", "leite", "ovos"),
                parser.parse(Intent.CHECKLIST, "Lista de compras: pão, arroz e feijão, leite e ovos.").fields().get("items"));
    }

    @Test
    void somaDuracoesERecusaAsInvalidas() {
        assertEquals(3900L, parser.parse(Intent.TIMER, "1 hora e 5 minutos de foco").fields().get("seconds"));
        assertEquals(5400L, parser.parse(Intent.TIMER, "cozinhar 1h30").fields().get("seconds"));
        assertEquals(5400L, parser.parse(Intent.TIMER, "1 hora e meia").fields().get("seconds"));
        assertEquals(1800L, parser.parse(Intent.TIMER, "meia hora pro bolo").fields().get("seconds"));
        assertEquals(0L, parser.parse(Intent.TIMER, "timer de 0 min").fields().get("seconds"));
        assertEquals(0L, parser.parse(Intent.TIMER, "timer de 48 horas").fields().get("seconds"));
        assertEquals(0L, parser.parse(Intent.TIMER, "timer de 1,5 hora").fields().get("seconds"));
        assertEquals(0L, parser.parse(Intent.TIMER, "timer de -5 minutos").fields().get("seconds"));
        assertEquals(0L, parser.parse(Intent.TIMER, "99999999999999999999 horas").fields().get("seconds"));
    }

    @Test
    void divideAteOCentavo() {
        var fields = parser.parse(Intent.SPLIT, "Dividir 10 entre 3").fields();
        assertEquals("3.33", fields.get("perPerson"));
        assertEquals(1L, fields.get("remainderCents"));

        var conta = parser.parse(Intent.SPLIT, "rachar a conta de R$ 1.200,50 por 3 pessoas").fields();
        assertEquals("1200.50", conta.get("total"));
        assertEquals("400.16", conta.get("perPerson"));

        assertFalse(parser.parse(Intent.SPLIT, "dividir 100 entre 0").warnings().isEmpty());
        assertFalse(parser.parse(Intent.SPLIT, "dividir 100 entre 3,5").warnings().isEmpty());
        assertFalse(parser.parse(Intent.SPLIT, "dividir 120.50 entre 3").warnings().isEmpty());
    }

    @Test
    void preservaNotasComoTexto() {
        assertEquals("<script>alert(1)</script>", parser.parse(Intent.NOTE, "<script>alert(1)</script>").fields().get("title"));
    }
}