import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.util.List;

public class ReactiveErrorHandlingDemo {

    // Mock domain classes
    static class User {
        String id;
        User(String id) { this.id = id; }
    }

    static class Order {
        String id;
        boolean invalid;
        Order(String id, boolean invalid) {
            this.id = id;
            this.invalid = invalid;
        }

        public boolean isInvalid() {
            return invalid;
        }

        public String getId() {
            return id;
        }
    }

    static class ProcessedOrder {
        String processedDetails;

        ProcessedOrder(String details) {
            this.processedDetails = details;
        }

        public String getProcessedDetails() {
            return processedDetails;
        }
    }

    // Entry point
    public static void main(String[] args) {
        Mono.just("user1")
            .flatMapMany(userId ->
                process(userId)
                    .onErrorResume(error -> {
                        System.err.println("Error fetching user details: " + error.getMessage());
                        return Mono.empty();
                    })
                    .flatMapMany(user ->
                        process(userId, user)
                            .onErrorResume(error -> {
                                System.err.println("Error fetching user orders: " + error.getMessage());
                                return Flux.empty();
                            })
                            .flatMapMany(order ->
                                safeProcess(userId, user, order) // checked + unchecked exceptions handled inside
                                    .onErrorResume(error -> {
                                        System.err.println("Error processing order: " + error.getMessage());
                                        return Flux.empty();
                                    })
                            )
                    )
            )
            .subscribe(
                processedOrder -> System.out.println("✅ Processed Order: " + processedOrder.getProcessedDetails()),
                error -> System.err.println("❌ Unhandled pipeline error: " + error.getMessage())
            );
    }

    // === Method 1: process(userId) ===
    public static Mono<User> process(String userId) {
        // Simulate valid fetch
        return Mono.just(new User(userId));
    }

    // === Method 2: process(userId, user) ===
    public static Flux<Order> process(String userId, User user) {
        // Simulate orders, some invalid
        return Flux.fromIterable(List.of(
            new Order("order1", false),
            new Order("badOrder", true),   // Will cause checked exception
            new Order("error", false)      // Will cause unchecked exception
        ));
    }

    // === Method 3: process(userId, user, order) — May throw checked/unchecked exceptions ===
    public static Flux<ProcessedOrder> process(String userId, User user, Order order) throws IOException {
        // Simulate checked exception
        if (order.isInvalid()) {
            throw new IOException("Invalid order: " + order.getId());
        }

        // Simulate unchecked exception
        if ("error".equals(order.getId())) {
            throw new IllegalArgumentException("Illegal order ID: " + order.getId());
        }

        return Flux.just(new ProcessedOrder("Processed " + order.getId()));
    }

    // === Helper to safely wrap checked exceptions ===
    public static Flux<ProcessedOrder> safeProcess(String userId, User user, Order order) {
        return Flux.defer(() -> {
            try {
                return process(userId, user, order);
            } catch (Exception e) {
                return Flux.error(e); // Convert checked exception to reactive error
            }
        });
    }
}
