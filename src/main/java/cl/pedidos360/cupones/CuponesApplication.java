package cl.pedidos360.cupones;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CuponesApplication {
    public static void main(String[] args) {
        SpringApplication.run(CuponesApplication.class, args);
    }
}
