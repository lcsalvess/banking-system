package com.lcsalvess.bankingsystem.integration.address;

import com.lcsalvess.bankingsystem.integration.address.config.AddressHttpClientConfig;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.springframework.http.client.JdkClientHttpRequestFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Local HTTP server that stands in for ViaCEP and Brasil API in client tests.
 * The clients build their own request factory, so the real HTTP stack is used
 * and only the remote provider is faked.
 */
public final class FakeAddressServer implements AutoCloseable {

    private final HttpServer server;
    private final List<String> requests = new CopyOnWriteArrayList<>();

    private volatile int status = 200;
    private volatile String body = "";
    private volatile Duration delay = Duration.ZERO;

    public FakeAddressServer() {
        try {
            server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        server.createContext("/", this::handle);
        server.start();
    }

    public void respondWith(int status, String body) {
        respondWith(status, body, Duration.ZERO);
    }

    public void respondWith(int status, String body, Duration delay) {
        this.status = status;
        this.body = body;
        this.delay = delay;
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Requests received so far, formatted as "METHOD /path". */
    public List<String> requests() {
        return List.copyOf(requests);
    }

    @Override
    public void close() {
        server.stop(0);
    }

    public static JdkClientHttpRequestFactory defaultRequestFactory() {
        return new AddressHttpClientConfig().addressRequestFactory();
    }

    public static JdkClientHttpRequestFactory shortTimeoutRequestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(500))
                .build();

        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(200));

        return requestFactory;
    }

    /** Base URL of a port where nothing is listening, to simulate a refused connection. */
    public static String closedPortBaseUrl() {
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            return "http://127.0.0.1:" + socket.getLocalPort();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private void handle(HttpExchange exchange) {
        requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI().getRawPath());

        try {
            Thread.sleep(delay.toMillis());

            byte[] content = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json;charset=UTF-8");

            if (content.length == 0) {
                exchange.sendResponseHeaders(status, -1);
            } else {
                exchange.sendResponseHeaders(status, content.length);
                try (OutputStream responseBody = exchange.getResponseBody()) {
                    responseBody.write(content);
                }
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
        } catch (IOException ignored) {
            // The client gave up before the response was written (timeout test).
        } finally {
            exchange.close();
        }
    }
}