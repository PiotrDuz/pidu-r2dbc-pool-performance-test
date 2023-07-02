import io.r2dbc.postgresql.PostgresqlConnectionFactoryProvider;
import io.r2dbc.spi.Connection;
import io.r2dbc.spi.ConnectionFactories;
import io.r2dbc.spi.ConnectionFactory;
import io.r2dbc.spi.ConnectionFactoryOptions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.resources.LoopResources;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;

import static io.r2dbc.pool.PoolingConnectionFactoryProvider.INITIAL_SIZE;
import static io.r2dbc.pool.PoolingConnectionFactoryProvider.MAX_SIZE;
import static io.r2dbc.spi.ConnectionFactoryOptions.*;

@Testcontainers
public class TestcontainersTest {

    @Container
    public PostgreSQLContainer postgres = new PostgreSQLContainer<>("postgres:12");
    public ConnectionFactory pooledConnectionFactory;
    Path initLocation = Paths.get("src", "test", "resources", "init.sql");

    ExecutorService executorService = Executors.newCachedThreadPool();

    private static void printThreadName(AtomicLong time, String text) {
        System.out.println(Thread.currentThread().getName() + " :t: " + time.get() + " :q: " + text);
    }


    @Test
    public void runWithColocation() throws InterruptedException, IOException {
        setupDb(builder -> builder);
        double average = runAllAndGetAverageTime(prepareHeavyAndManyLightQueries(50));
        System.out.println("Average time is: " + average);
    }

    @Test
    public void runWithoutColocation() throws InterruptedException, IOException {
        setupDb(builder -> builder.option(
                PostgresqlConnectionFactoryProvider.LOOP_RESOURCES,
                LoopResources.create("pref", -1, 4, true, false)));
        double average = runAllAndGetAverageTime(prepareHeavyAndManyLightQueries(50));
        System.out.println("Average time is: " + average);
    }

    private void setupDb(Function<Builder, Builder> builderModifier) throws IOException {
        ConnectionFactoryOptions.Builder optionsBuilder = builder()
                .option(DRIVER, "pool")
                .option(PROTOCOL, "postgresql") // driver identifier, PROTOCOL is delegated as DRIVER by the pool.
                .option(HOST, postgres.getHost())
                .option(PORT, postgres.getFirstMappedPort())
                .option(USER, postgres.getUsername())
                .option(PASSWORD, postgres.getPassword())
                .option(DATABASE, postgres.getDatabaseName())
                .option(INITIAL_SIZE, 4)
                .option(MAX_SIZE, 4);
        pooledConnectionFactory = ConnectionFactories.get(builderModifier.apply(optionsBuilder).build());
        String initCommand = Files.readString(initLocation);
        runQuery(initCommand).then().block();
    }

    @NotNull
    private List<Callable<Long>> prepareHeavyAndManyLightQueries(int lightQueriesSize) {
        List<Callable<Long>> callables = new ArrayList<>();
        callables.add(() -> measureQueryRunTime("select * from items"));
        for (int i = 0; i < lightQueriesSize; i++) {
            final int iname = i;
            callables.add(() -> measureQueryRunTime("select * from items where id = " + iname + 1));
        }
        return callables;
    }

    private double runAllAndGetAverageTime(List<Callable<Long>> callables) throws InterruptedException {
        AtomicLong totalQueryTime = new AtomicLong(0);
        executorService.invokeAll(callables).forEach(call -> {
            try {
                totalQueryTime.set(totalQueryTime.get() + call.get());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        return totalQueryTime.get() * 1.0 / callables.size();
    }

    private long measureQueryRunTime(String inputQuery) {
        AtomicLong time = new AtomicLong(0);
        Object row = Mono.just(inputQuery)
                .map(it -> Instant.now().toEpochMilli())
                .flatMapMany(start -> runQuery(inputQuery)
                        .doOnComplete(() -> time.set(Instant.now().toEpochMilli() - start)))
                .doOnComplete(() -> printThreadName(time, inputQuery))
                .blockLast();
        Assertions.assertNotNull(row);
        return time.get();
    }

    private Flux<Object> runQuery(String query) {
        return Flux.usingWhen(pooledConnectionFactory.create(),
                con -> Flux.from(con.createStatement(query).execute())
                        .flatMap(res -> res.map((row, meta) -> row))
                        .map(row -> row.get("name")),
                Connection::close);
    }
}
