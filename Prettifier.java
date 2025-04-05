import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Prettifier {

    public static final String GREEN = "\u001B[1;32m";
    public static final String YELLOW = "\u001B[1;33m";
    public static final String RED = "\u001B[1;31m";
    public static final String BLUE = "\u001B[1;34m";
    public static final String PURPLE = "\u001B[1;35m";
    public static final String RESET = "\u001B[0m";


    private static final Pattern IATA_PATTERN = Pattern.compile("#([A-Z]{3})");
    private static final Pattern ICAO_PATTERN = Pattern.compile("##([A-Z]{4})");

    private static final Pattern DATE_PATTERN = Pattern.compile("D\\((.*?)\\)");
    private static final Pattern TIME_12_PATTERN = Pattern.compile(//T12(2031-12-03T13:15:30+01:00)
            "T12\\((\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}([+-]\\d{2}:\\d{2}))\\)"
    );
    private static final Pattern TIME_24_PATTERN = Pattern.compile(
            "T24\\((\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}([+-]\\d{2}:\\d{2}))\\)"
    );
    private static final Pattern ZULU_TIME_PATTERN_12 = Pattern.compile("T12\\((.*?Z)\\)");
    private static final Pattern ZULU_TIME_PATTERN_24 = Pattern.compile("T24\\((.*?Z)\\)");
    private static final Pattern CITY_PATTERN = Pattern.compile("\\*(#[A-Z]{3}|##[A-Z]{4})");

    private static final DateTimeFormatter INPUT_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter ZULU_TIME_FORMATTER_12 = DateTimeFormatter.ofPattern("hh:mma (+00:00)");
    private static final DateTimeFormatter ZULU_TIME_FORMATTER_24 = DateTimeFormatter.ofPattern("HH:mm (+00:00)");
    private static final DateTimeFormatter TIME_12_FORMATTER = DateTimeFormatter.ofPattern("hh:mma (XXX)");
    private static final DateTimeFormatter TIME_24_FORMATTER = DateTimeFormatter.ofPattern("HH:mm (XXX)");


    private static Map<String, String> airportMap = new HashMap<>();
    private static Map<String, String> cityMap = new HashMap<>();

    public static void main(String[] args) {
        boolean printOutput = Arrays.asList(args).contains("--print");
        if (args.length < 3) {
            System.out.println(RED + "itinerary usage:\n$ java Prettifier.java ./input.txt ./output.txt ./airport-lookup.csv" + RESET);
            return;
        }
        if (Arrays.asList(args).contains("-h") || Arrays.asList(args).contains("--help")) {
            printHelp();
            return;
        }
        String inputFilePath = args[0];
        String outputFilePath = args[1];
        String lookupFilePath = args[2];

        // Kontrolli sisend- ja lookup-faili olemasolu
        if (!Files.exists(Paths.get(inputFilePath))) {
            System.out.println(RED + "Input not found" + RESET);
            return;
        }
        if (!Files.exists(Paths.get(lookupFilePath))) {
            System.out.println(RED + "Airport lookup not found" + RESET);
            return;
        }

        try {
            // Lae lennujaamade info
            loadAirportLookup(lookupFilePath);

            // Loe ja töötle sisend
            List<String> lines = Files.readAllLines(Paths.get(inputFilePath));
            String formattedText = processText(String.join("\n", lines));

            // Kirjuta väljund ainult kui kõik õnnestus
            Files.write(Paths.get(outputFilePath), formattedText.getBytes());
            if (Arrays.asList(args).contains("--print")) {
                System.out.println(formattedText);
            }

        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("Airport lookup")) {
                System.out.println(RED + "Airport lookup malformed" + RESET);
            } else {
                System.out.println(RED + "Error: " + e.getMessage() + RESET);
            }
        }
    }

    private static void loadAirportLookup(String filePath) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(filePath));
        if (lines.isEmpty()) {
            throw new IOException("Airport lookup malformed");
        }

        String[] headers = lines.get(0).split(",");
        Map<String, Integer> columnIndex = new HashMap<>();
        for (int i = 0; i < headers.length; i++) {
            columnIndex.put(headers[i].trim().toLowerCase(), i);
        }

        // Kontrolli vajalike veergude olemasolu
        if (!columnIndex.containsKey("name") || !columnIndex.containsKey("iata_code") ||
                !columnIndex.containsKey("icao_code") || !columnIndex.containsKey("municipality")) {
            throw new IOException("Airport lookup malformed");
        }

        // Tühjenda vanad andmed
        airportMap.clear();
        cityMap.clear();

        // Loe ja valideeri kõik read
        for (int i = 1; i < lines.size(); i++) {
            String[] columns = lines.get(i).split(",");
            if (columns.length < headers.length) {
                throw new IOException("Airport lookup malformed");
            }

            String name = columns[columnIndex.get("name")].trim();
            String city = columns[columnIndex.get("municipality")].trim();
            String iata = columns[columnIndex.get("iata_code")].trim();
            String icao = columns[columnIndex.get("icao_code")].trim();

            // Kontrolli, et ükski väärtus pole tühi
            if (name.isEmpty() || city.isEmpty() || iata.isEmpty() || icao.isEmpty()) {
                throw new IOException("Airport lookup malformed");
            }

            airportMap.put(iata, name);
            airportMap.put(icao, name);
            cityMap.put(iata, city);
            cityMap.put(icao, city);
        }

        // Kontrolli, et vähemalt üks kirje leiti
        if (airportMap.isEmpty()) {
            throw new IOException("Airport lookup malformed");
        }
    }

    private static String processText(String text) {
        // Process city patterns first
        Matcher cityMatcher = CITY_PATTERN.matcher(text);
        StringBuffer cityResult = new StringBuffer();
        while (cityMatcher.find()) {
            String code = cityMatcher.group(1);
            if (code.startsWith("#")) {
                code = code.substring(1); // Remove first #
                if (code.startsWith("#")) {
                    code = code.substring(1); // Remove second # if exists
                }
            }
            String replacement = cityMap.getOrDefault(code, cityMatcher.group());
            cityMatcher.appendReplacement(cityResult, Matcher.quoteReplacement(PURPLE + replacement + RESET));
        }
        cityMatcher.appendTail(cityResult);
        text = cityResult.toString();

        // Then process airport codes
        text = replacePattern(text, ICAO_PATTERN, airportMap, BLUE);
        text = replacePattern(text, IATA_PATTERN, airportMap, BLUE);

        // Finally process dates and times
        text = formatDateTime(text, DATE_PATTERN, DATE_FORMATTER, YELLOW);
        text = formatDateTime(text, TIME_12_PATTERN, TIME_12_FORMATTER, RED);
        text = formatDateTime(text, TIME_24_PATTERN, TIME_24_FORMATTER, GREEN);
        text = formatDateTime(text, ZULU_TIME_PATTERN_12, ZULU_TIME_FORMATTER_12, PURPLE);
        text = formatDateTime(text, ZULU_TIME_PATTERN_24, ZULU_TIME_FORMATTER_24, BLUE);
        text = text.replaceAll("[\u000B\u000C\r]+", "\n").replaceAll("\n{3,}", "\n\n"); //Eemaldab tühjad read, vertikaalne tabeldus
        return text;
    }

    private static String replacePattern(String text, Pattern pattern, Map<String, String> lookup, String color) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            String code = matcher.group(1);
            String matchedText = matcher.group();

            if (pattern.equals(CITY_PATTERN)) {
                // For city patterns, remove the # or ## prefix
                code = code.replaceAll("^#+", "");
            }

            String replacement = lookup.getOrDefault(code, matchedText);
            // Lisa jutumärgid ainult kui neid pole
            if (!replacement.startsWith("\"") && !replacement.endsWith("\"")) {
                replacement = "\"" + replacement + "\"";//siin lahendati jutumärgi küsimus
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(color + replacement + RESET));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String formatDateTime(String text, Pattern pattern, DateTimeFormatter formatter, String color) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer result = new StringBuffer();
        while (matcher.find()) {
            try {
                OffsetDateTime dateTime = OffsetDateTime.parse(matcher.group(1), INPUT_FORMATTER);
                String formattedDate = dateTime.format(formatter);
                matcher.appendReplacement(result, color + formattedDate + RESET);
            } catch (Exception e) {
                matcher.appendReplacement(result, matcher.group());
            }
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static void printHelp() {
        System.out.println(BLUE + "The program converts airport codes into airport names and formats dates and times into a human-readable form." + RESET);
        System.out.println(BLUE + "Please enter the information into the 'input.txt' file using the following format:" + RESET);
        System.out.println("  - IATA airport codes:" + YELLOW + " #LHR" + RESET);
        System.out.println("  - ICAO airport codes:" + YELLOW + " ##EGLL" + RESET);
        System.out.println("  - TIME in ISO 8601 form:");
        System.out.println(RED + "\nThe results will be saved in the 'output.txt' file.\n" + RESET);
    }

}


