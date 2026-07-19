import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    private static final Pattern PRICE_PATTERN = Pattern.compile("\"price\"\\s*:\\s*\"([^\"]+)\"");

    private static final Queue<PricePoint> priceQueue = new ArrayDeque<>();
    private static final HttpClient httpClient = HttpClient.newHttpClient();

    public static void main(String[] args) {
        String apiKey = System.getenv("TWELVE_DATA_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            System.err.println("Missing TWELVE_DATA_API_KEY environment variable.");
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
        try {
            String price = fetchPrice(apiKey);
            Instant timestamp = Instant.now();
            priceQueue.add(new PricePoint(price, timestamp));

            System.out.printf("Added data point: price=%s, timestamp=%s%n", price, timestamp);
            System.out.println("Current queue size: " + priceQueue.size());
        } catch (IOException | InterruptedException e) {
            System.err.println("Failed to fetch price: " + e.getMessage());
        }
    }

    private static String fetchPrice(String apiKey) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_URL + apiKey))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return extractPrice(response.body());
    }

    private static String extractPrice(String jsonBody) {
        Matcher matcher = PRICE_PATTERN.matcher(jsonBody);
        if (matcher.find()) {
            return matcher.group(1);
        }
        throw new IllegalStateException("Unexpected API response: " + jsonBody);
    }

    private static final class PricePoint {
        final String price;
        final Instant timestamp;

        PricePoint(String price, Instant timestamp) {
            this.price = price;
            this.timestamp = timestamp;
        }
    }
}
