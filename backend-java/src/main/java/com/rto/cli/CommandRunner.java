package com.rto.cli;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** `--rto.command=bootstrap [--sample-data]`: seed RBAC + payable types and create the first admin, then exit. */
@Component
public class CommandRunner implements ApplicationRunner {
    private final BootstrapService bootstrap;
    private final String command;

    public CommandRunner(BootstrapService bootstrap, @Value("${rto.command:}") String command) {
        this.bootstrap = bootstrap;
        this.command = command;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!"bootstrap".equals(command)) return;
        bootstrap.seedRbac();
        bootstrap.seedPayableTypes();
        if (args.containsOption("sample-data")) bootstrap.seedSample();
        System.out.println(bootstrap.createAdmin());
    }
}
