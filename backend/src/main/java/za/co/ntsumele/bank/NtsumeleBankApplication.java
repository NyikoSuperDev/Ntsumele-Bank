package za.co.ntsumele.bank;

import java.time.Clock;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * class : NtsumeleBankApplication
 * Author : Nyiko Chauke
 * 2026
 *
 * Starts the Spring Boot banking application and provides the shared UTC clock used for timestamps and banking rules.
 */
@SpringBootApplication
@EnableScheduling
public class NtsumeleBankApplication {

    public static void main(String[] arguments) {
        SpringApplication.run(NtsumeleBankApplication.class, arguments);
    }

    @Bean
    public Clock bankingClock() {
        return Clock.systemUTC();
    }
}
