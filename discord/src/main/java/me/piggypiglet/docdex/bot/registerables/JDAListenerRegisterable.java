package me.piggypiglet.docdex.bot.registerables;

import com.google.inject.Inject;
import com.google.inject.Injector;
import me.piggypiglet.docdex.bootstrap.framework.Registerable;
import me.piggypiglet.docdex.scanning.framework.Scanner;
import me.piggypiglet.docdex.scanning.rules.Rules;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

// ------------------------------
// Copyright (c) PiggyPiglet 2020
// https://www.piggypiglet.me
// ------------------------------
public final class JDAListenerRegisterable extends Registerable {
    private final Scanner scanner;
    private final JDA jda;

    @Inject
    public JDAListenerRegisterable(@NotNull final Scanner scanner, @NotNull final JDA jda) {
        this.scanner = scanner;
        this.jda = jda;
    }

    @Override
    public void execute(@NotNull final Injector injector) {
        scanner.getClasses(Rules.builder().typeExtends(ListenerAdapter.class).disallowMutableClasses().build())
                .map(injector::getInstance)
                .forEach(jda::addEventListener);
    }
}
