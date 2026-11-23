package br.udq;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.ToLongFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class CardParser {

    // UNICODE_CHARACTER_CLASS faz \b e \w reconhecerem letras acentuadas (ã, á, ç).
    // Sem ela, o Java não acha "amanhã" nem "às" com \b, porque trata o acento como "não letra".
    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;

    // ---------- Timer ----------

    // Cada regra reconhece um jeito de escrever duração. São aplicadas em ordem,
    // e o trecho reconhecido é apagado do texto para não ser contado duas vezes.
    private record DurationRule(Pattern pattern, ToLongFunction<Matcher> seconds) {
    }

    private static final List<DurationRule> DURATION_RULES = List.of(
            // "1 hora e meia", "2h e meia"
            new DurationRule(Pattern.compile("(?<![\\w.,\\-])(\\d+)\\s*(?:horas?|h)\\s+e\\s+meia\\b", FLAGS),
                    m -> Math.addExact(Math.multiplyExact(Long.parseLong(m.group(1)), 3600), 1800)),
            // "1h30", "1h30min", "1 h 30"
            new DurationRule(Pattern.compile("(?<![\\w.,\\-])(\\d+)\\s*h\\s*(\\d{2})(?:\\s*min(?:utos?)?)?(?!\\w)", FLAGS),
                    m -> Math.addExact(Math.multiplyExact(Long.parseLong(m.group(1)), 3600),
                            Math.multiplyExact(Long.parseLong(m.group(2)), 60))),
            // "meia hora"
            new DurationRule(Pattern.compile("\\bmeia\\s+hora\\b", FLAGS), m -> 1800),
            // "10 minutos", "2 horas", "30s", "1 hora e 15 min"
            new DurationRule(Pattern.compile(
                    "(?<![\\w.,\\-])(\\d+)\\s*(horas?|hrs?|h|minutos?|mins?|m|segundos?|segs?|s)\\b", FLAGS),
                    m -> Math.multiplyExact(Long.parseLong(m.group(1)), unitSeconds(m.group(2)))));

    // ---------- Divisão de conta ----------

    // Valor no formato brasileiro: 1.200,50 | 1200,50 | 120 | 1.200
    private static final String AMOUNT = "(\\d{1,3}(?:\\.\\d{3})+(?:,\\d{1,2})?|\\d+(?:,\\d{1,2})?)";

    // "dividir 120,50 entre 3", "rachar a conta de R$ 90 por 4 pessoas"
    private static final Pattern SPLIT = Pattern.compile(
            "(?:divid\\w*|rach\\w*)\\D{0,30}?" + AMOUNT
                    + "\\s*(?:reais|real)?\\s*(?:entre|por|para|/)\\s*(\\d+)(?![.,]?\\d)(?!\\w)",
            FLAGS);

    // ---------- Checklist ----------

    private static final Pattern CHECKLIST_PREFIX = Pattern.compile(
            "^(?:lista de compras|compras|comprar|checklist|lista|tarefas|a fazer)\\s*:?\\s*", FLAGS);
    private static final Pattern SEPARATORS = Pattern.compile("\\s*[,;\\n]\\s*");
    private static final Pattern AND = Pattern.compile("\\s+e\\s+", FLAGS);
    private static final Pattern TRAILING_PUNCTUATION = Pattern.compile("[.!]+$");

    // ---------- Data ----------

    private static final Pattern ISO_DATE = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    // 25/12, 25/12/2026, 5/1/27
    private static final Pattern BR_DATE = Pattern.compile("(?<![\\d/])(\\d{1,2})/(\\d{1,2})(?:/(\\d{4}|\\d{2}))?(?![\\d/])");
    private static final Pattern DAY_AFTER_TOMORROW = Pattern.compile("\\bdepois\\s+de\\s+amanh[ãa]\\b", FLAGS);
    private static final Pattern TOMORROW = Pattern.compile("\\bamanh[ãa]\\b", FLAGS);
    private static final Pattern TODAY = Pattern.compile("\\bhoje\\b", FLAGS);
    private static final Map<DayOfWeek, Pattern> WEEKDAYS = Map.of(
            DayOfWeek.MONDAY, Pattern.compile("\\bsegunda\\b", FLAGS),
            DayOfWeek.TUESDAY, Pattern.compile("\\bter[çc]a\\b", FLAGS),
            DayOfWeek.WEDNESDAY, Pattern.compile("\\bquarta\\b", FLAGS),
            DayOfWeek.THURSDAY, Pattern.compile("\\bquinta\\b", FLAGS),
            DayOfWeek.FRIDAY, Pattern.compile("\\bsexta\\b", FLAGS),
            DayOfWeek.SATURDAY, Pattern.compile("\\bs[áa]bado\\b", FLAGS),
            DayOfWeek.SUNDAY, Pattern.compile("\\bdomingo\\b", FLAGS));

    // ---------- Hora ----------

    // "da manhã", "da tarde"... opcional depois da hora
    private static final String PERIOD = "(?:\\s*(da\\s+manh[ãa]|da\\s+tarde|da\\s+noite|da\\s+madrugada))?";
    // 20h, 20h30, 8h15, 20:30, "8h da noite"
    private static final Pattern TIME = Pattern.compile(
            "(?<![\\d:/])(\\d{1,2})(?:h(\\d{2})?|:(\\d{2}))(?!\\w)" + PERIOD, FLAGS);
    // "às 8 da noite", "as 20"
    private static final Pattern TIME_AFTER_AS = Pattern.compile("\\b[àa]s\\s+(\\d{1,2})(?![\\w:])" + PERIOD, FLAGS);
    // "3 da tarde", "8 da noite": sem "h" e sem "às", o período é obrigatório
    private static final Pattern TIME_WITH_PERIOD = Pattern.compile(
            "(?<![\\d:/])(\\d{1,2})\\s+(da\\s+manh[ãa]|da\\s+tarde|da\\s+noite|da\\s+madrugada)", FLAGS);
    private static final Pattern NOON = Pattern.compile("\\bmeio[- ]dia\\b", FLAGS);
    private static final Pattern MIDNIGHT = Pattern.compile("\\bmeia[- ]noite\\b", FLAGS);

    private final Clock clock;

    public record Card(String type, Map<String, Object> fields, List<String> warnings) {
    }

    @Inject
    public CardParser(ShapeConfig config) {
        this(Clock.system(ZoneId.of(config.zone())));
    }

    CardParser(Clock clock) {
        this.clock = clock;
    }

    Card parse(Intent intent, String text) {
        var warnings = new ArrayList<String>();
        Map<String, Object> fields = switch (intent) {
            case EVENT, REMINDER -> schedule(text, warnings);
            case CHECKLIST -> checklist(text, warnings);
            case TIMER -> timer(text, warnings);
            case SPLIT -> split(text, warnings);
            case NOTE, NONE -> Map.of("title", text);
        };
        return new Card(intent.name(), fields, warnings);
    }

    private Map<String, Object> checklist(String text, List<String> warnings) {
        String list = CHECKLIST_PREFIX.matcher(text.trim()).replaceFirst("");
        var items = splitItems(list);
        if (items.size() < 2) {
            warnings.add("Só um item foi encontrado. Edite a lista se precisar.");
        }
        return Map.of("items", items);
    }

    private List<String> splitItems(String list) {
        List<String> parts = new ArrayList<>(Arrays.asList(SEPARATORS.split(list)));
        if (parts.size() == 1) {
            // Sem vírgulas ("leite e ovos"): cada "e" separa um item.
            parts = new ArrayList<>(Arrays.asList(AND.split(list)));
        } else {
            // Com vírgulas ("pão, arroz e feijão, leite e ovos"): em português o "e" só
            // aparece antes do último item. Então só o último "e" do último trecho separa,
            // e "arroz e feijão" no meio da lista continua sendo um item só.
            String last = parts.remove(parts.size() - 1);
            Matcher and = AND.matcher(last);
            int cutStart = -1;
            int cutEnd = -1;
            while (and.find()) {
                cutStart = and.start();
                cutEnd = and.end();
            }
            if (cutStart >= 0) {
                parts.add(last.substring(0, cutStart));
                parts.add(last.substring(cutEnd));
            } else {
                parts.add(last);
            }
        }
        return parts.stream()
                .map(item -> TRAILING_PUNCTUATION.matcher(item.trim()).replaceAll(""))
                .filter(item -> !item.isBlank())
                .toList();
    }

    private Map<String, Object> timer(String text, List<String> warnings) {
        long seconds = 0;
        String rest = text;
        try {
            for (DurationRule rule : DURATION_RULES) {
                Matcher matcher = rule.pattern().matcher(rest);
                while (matcher.find()) {
                    seconds = Math.addExact(seconds, rule.seconds().applyAsLong(matcher));
                }
                rest = matcher.replaceAll(" ");
            }
        } catch (ArithmeticException | NumberFormatException e) {
            // Número grande demais para um long: cai no aviso abaixo em vez de virar erro 500.
            seconds = -1;
        }
        if (seconds < 1 || seconds > 86400) {
            warnings.add("Informe uma duração entre 1 segundo e 24 horas.");
            seconds = 0;
        }
        return Map.of("title", text, "seconds", seconds);
    }

    private static long unitSeconds(String unit) {
        String u = unit.toLowerCase(Locale.ROOT);
        if (u.startsWith("h")) {
            return 3600;
        }
        if (u.startsWith("m")) {
            return 60;
        }
        return 1;
    }

    private Map<String, Object> split(String text, List<String> warnings) {
        var matcher = SPLIT.matcher(text);
        if (!matcher.find()) {
            warnings.add("Use o formato: dividir 120,50 entre 3.");
            return Map.of("total", "", "people", 0, "perPerson", "", "remainderCents", 0);
        }
        var total = new BigDecimal(toMachineFormat(matcher.group(1)));
        int people = Integer.parseInt(matcher.group(2));
        if (people < 1 || people > 1000 || total.compareTo(new BigDecimal("1000000")) > 0) {
            warnings.add("Use de 1 a 1000 pessoas e um valor de até 1.000.000.");
            return Map.of("total", total.toPlainString(), "people", 0, "perPerson", "", "remainderCents", 0);
        }
        long cents = total.movePointRight(2).longValueExact();
        // A saída continua com ponto decimal ("120.50"): é o formato que o frontend recebe.
        // Exibir com vírgula é trabalho da tela, não do parser.
        return Map.of("total", total.setScale(2).toPlainString(), "people", people,
                "perPerson", BigDecimal.valueOf(cents / people, 2).setScale(2, RoundingMode.UNNECESSARY).toPlainString(),
                "remainderCents", cents % people);
    }

    // "1.200,50" -> "1200.50": tira o ponto de milhar e troca a vírgula decimal por ponto.
    private static String toMachineFormat(String amount) {
        return amount.replace(".", "").replace(',', '.');
    }

    private Map<String, Object> schedule(String text, List<String> warnings) {
        LocalDate day = null;
        LocalDate today = LocalDate.now(clock);
        var iso = ISO_DATE.matcher(text);
        var br = BR_DATE.matcher(text);
        try {
            if (iso.find()) {
                day = LocalDate.parse(iso.group());
            } else if (br.find()) {
                day = brazilianDate(br, today);
            } else if (DAY_AFTER_TOMORROW.matcher(text).find()) {
                day = today.plusDays(2);
            } else if (TOMORROW.matcher(text).find()) {
                day = today.plusDays(1);
            } else if (TODAY.matcher(text).find()) {
                day = today;
            } else {
                for (DayOfWeek weekday : DayOfWeek.values()) {
                    if (WEEKDAYS.get(weekday).matcher(text).find()) {
                        day = today.with(TemporalAdjusters.next(weekday));
                        break;
                    }
                }
            }
        } catch (RuntimeException e) {
            warnings.add("A data é inválida.");
        }

        LocalTime time = null;
        try {
            var match = TIME.matcher(text);
            var afterAs = TIME_AFTER_AS.matcher(text);
            var withPeriod = TIME_WITH_PERIOD.matcher(text);
            if (match.find()) {
                int hour = Integer.parseInt(match.group(1));
                String minutes = match.group(2) != null ? match.group(2) : match.group(3);
                int minute = minutes == null ? 0 : Integer.parseInt(minutes);
                time = LocalTime.of(applyPeriod(hour, match.group(4)), minute);
            } else if (afterAs.find()) {
                time = LocalTime.of(applyPeriod(Integer.parseInt(afterAs.group(1)), afterAs.group(2)), 0);
            } else if (withPeriod.find()) {
                time = LocalTime.of(applyPeriod(Integer.parseInt(withPeriod.group(1)), withPeriod.group(2)), 0);
            } else if (NOON.matcher(text).find()) {
                time = LocalTime.NOON;
            } else if (MIDNIGHT.matcher(text).find()) {
                time = LocalTime.MIDNIGHT;
            }
        } catch (RuntimeException e) {
            warnings.add("O horário é inválido.");
        }

        if (day == null || time == null) {
            warnings.add("Confirme data e hora. Exemplos: amanhã, sexta, 25/12; 20h, 20h30 ou às 8 da noite.");
        }
        return Map.of("title", text, "date", day == null ? "" : day.toString(), "time", time == null ? "" : time.toString(),
                "zone", clock.getZone().getId());
    }

    // Sem ano, usa o ano atual; se a data já passou, entende que é no ano que vem.
    private static LocalDate brazilianDate(Matcher m, LocalDate today) {
        int day = Integer.parseInt(m.group(1));
        int month = Integer.parseInt(m.group(2));
        String yearText = m.group(3);
        if (yearText != null) {
            int year = Integer.parseInt(yearText);
            return LocalDate.of(yearText.length() == 2 ? 2000 + year : year, month, day);
        }
        LocalDate date = LocalDate.of(today.getYear(), month, day);
        return date.isBefore(today) ? date.plusYears(1) : date;
    }

    // Converte "8 da noite" em 20h. Se a hora já estiver em 24h ("20h da noite"), não mexe.
    private static int applyPeriod(int hour, String period) {
        if (period == null || hour > 12) {
            return hour;
        }
        String p = period.toLowerCase(Locale.ROOT);
        if (p.endsWith("tarde") || p.endsWith("noite")) {
            if (hour == 12) {
                return p.endsWith("noite") ? 0 : 12;
            }
            return hour + 12;
        }
        return hour == 12 ? 0 : hour;
    }
}