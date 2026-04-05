import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class CurrencyConverterApp {

    private static CurrencyStore store = new CurrencyStore();
    private static CurrencyConverter converter = new CurrencyConverter();

    public static void main(String[] args) throws IOException {
        // Initialize currencies with provided rates
        store.addCurrency(new Currency("USD", 1.0));
        store.addCurrency(new Currency("INR", 92.98));
        store.addCurrency(new Currency("EUR", 0.87));
        store.addCurrency(new Currency("YEN", 159.57));
        store.addCurrency(new Currency("GBP", 0.76));
        store.addCurrency(new Currency("BIT", 0.063));

        // Start server on port 8080
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", new RootHandler());
        server.createContext("/style.css", new StaticFileHandler("style.css", "text/css"));
        server.setExecutor(null);
        server.start();

        System.out.println("Server started on http://localhost:8080");
        System.out.println("Exchange Rates (1 USD =):");
        System.out.println("INR: 92.98, EUR: 0.87, YEN: 159.57, GBP: 0.76, BIT: 0.063");
    }

    // Handles the main page and conversion results
    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String method = exchange.getRequestMethod();
            String responseHTML = readTemplate("index.html");

            if ("GET".equalsIgnoreCase(method)) {
                Map<String, String> params = parseQueryParams(exchange.getRequestURI().getQuery());
                if (params.containsKey("amount") && params.containsKey("from") && params.containsKey("to")) {
                    try {
                        double amount = Double.parseDouble(params.get("amount"));
                        String fromCode = params.get("from");
                        String toCode = params.get("to");

                        Currency from = store.getCurrency(fromCode);
                        Currency to = store.getCurrency(toCode);

                        if (from != null && to != null) {
                            double result = converter.convert(amount, from, to);
                            String resultHtml = String.format(
                                "<div class='result-card animated'>\n" +
                                "  <span class='result-label'>%s %s =</span>\n" +
                                "  <span class='result-value'>%.4f %s</span>\n" +
                                "</div>",
                                amount, fromCode, result, toCode
                            );
                            responseHTML = responseHTML.replace("<!-- RESULT_PLACEHOLDER -->", resultHtml);
                            
                            // Re-inject values into inputs
                            responseHTML = responseHTML.replace("value=\"\" id=\"amount\"", "value=\"" + amount + "\" id=\"amount\"");
                            responseHTML = responseHTML.replace("data-selected-from=\"" + fromCode + "\"", "selected");
                            responseHTML = responseHTML.replace("data-selected-to=\"" + toCode + "\"", "selected");
                        } else {
                            responseHTML = responseHTML.replace("<!-- RESULT_PLACEHOLDER -->", "<div class='error-msg'>Invalid Currency Code</div>");
                        }
                    } catch (Exception e) {
                        responseHTML = responseHTML.replace("<!-- RESULT_PLACEHOLDER -->", "<div class='error-msg'>Invalid Input</div>");
                    }
                } else {
                    responseHTML = responseHTML.replace("<!-- RESULT_PLACEHOLDER -->", "");
                }
            }

            byte[] bytes = responseHTML.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }

    // Handles static file serving
    static class StaticFileHandler implements HttpHandler {
        private String fileName;
        private String contentType;

        public StaticFileHandler(String fileName, String contentType) {
            this.fileName = fileName;
            this.contentType = contentType;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            File file = new File(fileName);
            if (!file.exists()) {
                exchange.sendResponseHeaders(404, -1);
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, file.length());
            OutputStream os = exchange.getResponseBody();
            Files.copy(file.toPath(), os);
            os.close();
        }
    }

    private static String readTemplate(String path) throws IOException {
        return Files.readString(new File(path).toPath(), StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseQueryParams(String query) {
        Map<String, String> params = new HashMap<>();
        if (query == null) return params;
        String[] pairs = query.split("&");
        for (String pair : pairs) {
            int idx = pair.indexOf("=");
            try {
                String key = URLDecoder.decode(pair.substring(0, idx), "UTF-8");
                String value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8");
                params.put(key, value);
            } catch (Exception e) {}
        }
        return params;
    }
}

// Logic classes from reference code
class Currency {
    private String code;
    private double rate;

    public Currency(String code, double rate) {
        this.code = code;
        this.rate = rate;
    }

    public String getCode() { return code; }
    public double getRate() { return rate; }
}

class CurrencyStore {
    private Map<String, Currency> currencies = new HashMap<>();

    public void addCurrency(Currency currency) {
        currencies.put(currency.getCode(), currency);
    }

    public Currency getCurrency(String code) {
        return currencies.get(code);
    }
}

class CurrencyConverter {
    public double convert(double amount, Currency from, Currency to) {
        if (from == null || to == null) return 0;
        double baseAmount = amount / from.getRate();
        return baseAmount * to.getRate();
    }
}
