package me.piggypiglet.docdex.documentation.index.data.population;

import me.piggypiglet.docdex.config.Javadoc;
import me.piggypiglet.docdex.documentation.objects.DocumentedObject;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

// ------------------------------
// Copyright (c) PiggyPiglet 2020
// https://www.piggypiglet.me
// ------------------------------
public interface IndexPopulator {
    boolean shouldPopulate(@NotNull final Javadoc javadoc);

    @NotNull
    Map<String, DocumentedObject> provideObjects(@NotNull final Javadoc javadoc);
}
