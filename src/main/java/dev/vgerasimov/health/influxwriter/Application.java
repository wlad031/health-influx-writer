package dev.vgerasimov.health.influxwriter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

@SpringBootConfiguration
@EnableWebMvc
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
@ComponentScan(
    basePackages = {
      "dev.vgerasimov.pipes.registrar",
      "dev.vgerasimov.pipes.web",
    })
public class Application {

  public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
  }
}
