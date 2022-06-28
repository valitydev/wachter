package dev.vality.wachter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.servlet.ServletComponentScan;

@ServletComponentScan
@SpringBootApplication
public class WachterApplication extends SpringApplication {

    public static void main(String[] args) {
        SpringApplication.run(WachterApplication.class, args);
    }

}
