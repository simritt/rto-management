package com.rto;

import com.rto.cli.AdminCli;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.Arrays;

@SpringBootApplication(exclude = org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class RtoApplication {

    public static void main(String[] args) throws Exception {
        // Commands that must work before the application's own database user exists run WITHOUT Spring.
        if (Arrays.asList(args).contains("--rto.command=setup-mysql")) System.exit(AdminCli.setupMysql(args));
        if (Arrays.asList(args).contains("--rto.command=apply-schema")) System.exit(AdminCli.applySchema(args));

        SpringApplication app = new SpringApplication(RtoApplication.class);
        if (Arrays.stream(args).anyMatch(a -> a.startsWith("--rto.command="))) {
            app.setWebApplicationType(WebApplicationType.NONE);   // bootstrap needs the database but no web server
        }
        app.run(args);
    }
}
