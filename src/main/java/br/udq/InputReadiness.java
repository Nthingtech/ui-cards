package br.udq;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Checagens conservadoras de sintaxe, compartilhadas com o navegador, antes da inferência automática.
 *
 * Atenção: estas regex rodam em Java E em JavaScript (o navegador recebe os padrões via /api/config).
 * Por isso elas evitam recursos que só um dos dois entende. Em especial, não usam \b colado em letra
 * acentuada: tanto em Java (sem UNICODE_CHARACTER_CLASS) quanto em JavaScript, \b só enxerga letras
 * sem acento. No lugar, o início de palavra é marcado com (?:^|\s).
 */
@ApplicationScoped
public class InputReadiness {
    public record Rule(String pattern, String exception, String reason) {
        boolean matches(String text) {
            return find(pattern, text) && (exception == null || !find(exception, text));
        }

        private boolean find(String expression, String text) {
            return Pattern.compile(expression, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(text).find();
        }
    }

    private static final List<Rule> RULES = List.of(
            // Termina com vírgula, ponto e vírgula ou dois-pontos: "comprar pão, leite,"
            new Rule("[,;:]\\s*$", null, "Termine o item ou o detalhe atual."),

            // Termina com preposição, artigo ou conjunção: "reunião amanhã às", "comprar pão e"
            // Exceção: perguntas ("o que você quer?") podem terminar assim.
            new Rule("(?:^|\\s)(?:a|o|as|os|um|uma|uns|umas|de|do|da|dos|das|em|no|na|nos|nas|ao|aos|à|às"
                    + "|para|pra|pro|por|pelo|pela|com|sem|e|ou|até|entre|sobre|que)\\s*[.!?…]*$",
                    "^(?:o\\s+que|que|quem|onde|quando|por\\s*qu[eê]|como|qual|quais|quant[oa]s?)(?=[\\s?]).*\\?\\s*$",
                    "Complete o detalhe depois da última palavra."),

            // Só o comando, sem conteúdo: "lembrar", "comprar", "dividir", "criar um timer"
            new Rule("^(?:por\\s+favor,?\\s+)?(?:me\\s+)?(?:lembr(?:ar|e|a)(?:-me)?(?:\\s+de)?|lembrete"
                    + "|n[ãa]o\\s+esquecer(?:\\s+de)?|comprar|compras|lista(?:\\s+de\\s+compras)?|checklist|tarefas"
                    + "|a\\s+fazer|dividir|divide|rachar|racha|foco"
                    + "|(?:(?:criar|iniciar|come[çc]ar|colocar|p[ôo]r|ligar)\\s+)?(?:um\\s+)?"
                    + "(?:timer|temporizador|cron[ôo]metro|contagem\\s+regressiva))\\s*[.!?…]*$",
                    null, "Informe a tarefa, os itens, o valor ou a duração."),

            // Horário pela metade: "às 8", "20:", "20:3", "20h3"
            new Rule("(?:(?:^|\\s)[àa]s\\s+\\d{1,2}|\\d{1,2}(?::\\d?|h\\d))\\s*$", null,
                    "Termine o horário, como 20h, 20:30 ou às 8 da noite."),

            // Começou a dividir, mas ainda falta o valor ou as pessoas.
            // A exceção usa o mesmo formato que o CardParser aceita: "dividir 120,50 entre 3".
            new Rule("^(?:por\\s+favor,?\\s+)?(?:dividir|divide|divida|rachar|racha)\\b",
                    "(?:divid\\w*|rach\\w*)\\D{0,30}?(?:\\d{1,3}(?:\\.\\d{3})+(?:,\\d{1,2})?|\\d+(?:,\\d{1,2})?)"
                            + "\\s*(?:reais|real)?\\s*(?:entre|por|para|/)\\s*[1-9]\\d*(?![.,]?\\d)(?!\\w)",
                    "Informe o valor e o número de pessoas, como: dividir 120,50 entre 3."));

    public List<Rule> rules() {
        return RULES;
    }

    public String waitingReason(String text) {
        String trimmed = text.trim();
        if (trimmed.length() < 2) {
            return "Continue digitando.";
        }
        for (Rule rule : RULES) {
            if (rule.matches(trimmed)) {
                return rule.reason();
            }
        }
        return null;
    }
}