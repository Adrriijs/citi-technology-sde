import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class App {

    private static final String SYMBOL = "DIA";
    private static final String API_URL = "https://api.twelvedata.com/price?symbol=" + SYMBOL + "&apikey=";
    private static final long POLL_INTERVAL_SECONDS = 15;
    private static final Pattern PRICE_PATTERN = Pattern.compile("\"price\"\\s*:\\s*\"?([0-9]+(?:\\.[0-9]+)?)\"?");

    // Written relative to the src/ working directory the app is run from,
    // so it lands in <project root>/data/prices.jsonl for the notebook to read.
    private static final Path OUTPUT_FILE = Path.of("..", "data", "prices.jsonl");

    private static final Queue<PricePoint> priceQueue = new ArrayDeque<>();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static void main(String[] args) {
        String apiKey = System.getenv("TWELVE_DATA_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Missing TWELVE_DATA_API_KEY environment variable.");
            System.exit(1);
        }

        try {
            Files.createDirectories(OUTPUT_FILE.getParent());
            // Start each run with a clean file so the notebook only ever plots
            // the points collected during this run, not prior runs' leftovers.
            Files.writeString(OUTPUT_FILE, "", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            System.err.println("Failed to prepare output file: " + e.getMessage());
            System.exit(1);
        }

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        scheduler.scheduleAtFixedRate(
                () -> fetchAndStorePrice(apiKey),
                0,
                POLL_INTERVAL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    private static void fetchAndStorePrice(String apiKey) {
        // Catch broadly: an uncaught exception here would silently cancel all
        // future runs of this scheduled task, stopping the poller with no output.
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL + apiKey))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.println("Unable to retrieve market data. HTTP status code: " + response.statusCode());
                return;
            }

            Double price = extractPrice(response.body());
            if (price == null) {
                System.err.println("Could not find a price field in the response: " + response.body());
                return;
            }

            Instant timestamp = Instant.now();
            PricePoint point = new PricePoint(price, timestamp);
            priceQueue.add(point);
            appendToFile(point);

            System.out.printf("Added data point: price=%s, timestamp=%s%n", price, timestamp);
            System.out.println("Current queue size: " + priceQueue.size());
        } catch (Exception e) {
            System.err.println("Failed to fetch price: " + e.getMessage());
        }
    }

    private static Double extractPrice(String jsonBody) {
        Matcher matcher = PRICE_PATTERN.matcher(jsonBody);
        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }
        return null;
    }

    private static void appendToFile(PricePoint point) {
        String line = String.format("{\"price\":%s,\"timestamp\":\"%s\"}\n", point.price, point.timestamp);
        try {
            Files.writeString(OUTPUT_FILE, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            System.err.println("Failed to write data point to file: " + e.getMessage());
        }
    }

    private static final class PricePoint {
        final double price;
        final Instant timestamp;

        PricePoint(double price, Instant timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }
    }
}
