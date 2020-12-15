package me.piggypiglet.docdex.documentation.index.data.population.implementations.web;

import me.piggypiglet.docdex.config.Javadoc;
import me.piggypiglet.docdex.documentation.index.data.population.IndexPopulator;
import me.piggypiglet.docdex.documentation.index.data.utils.DataUtils;
import me.piggypiglet.docdex.documentation.objects.DocumentedObject;
import me.piggypiglet.docdex.documentation.objects.DocumentedTypes;
import me.piggypiglet.docdex.documentation.objects.type.TypeMetadata;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

// ------------------------------
// Copyright (c) PiggyPiglet 2020
// https://www.piggypiglet.me
// ------------------------------
public final class WebCrawlPopulator implements IndexPopulator {
    private static final Logger LOGGER = LoggerFactory.getLogger("WebCrawlPopulator");
    private static final Set<String> TYPE_NAMES = Stream.of(
            DocumentedTypes.CLASS, DocumentedTypes.INTERFACE,
            DocumentedTypes.ANNOTATION, DocumentedTypes.ENUM
    ).map(DocumentedTypes::getName).map(String::toLowerCase).collect(Collectors.toSet());

    @Override
    public boolean shouldPopulate(final @NotNull Javadoc javadoc) {
        return !(new File("docs", String.join("-", javadoc.getNames()) + ".json").exists());
    }

    @NotNull
    @Override
    public Map<String, DocumentedObject> provideObjects(@NotNull final Javadoc javadoc) {
        final String javadocName = DataUtils.getName(javadoc);
        final Document mainDocument = connect(javadoc.getLink());

        if (mainDocument == null) {
            return Collections.emptyMap();
        }

        final Optional<Element> indexAnchor = mainDocument.select("ul.navList > li > a").stream()
                .filter(element -> element.text().equalsIgnoreCase("index"))
                .findAny();

        if (indexAnchor.isEmpty()) {
            return Collections.emptyMap();
        }

        final Document document = connect(indexAnchor.get().absUrl("href"));

        if (document == null) {
            return Collections.emptyMap();
        }

        final Set<DocumentedObject> objects = new HashSet<>();
        final Set<Element> types = document.select("dl > dt > a").stream()
                .filter(element -> TYPE_NAMES.stream().anyMatch(element.attr("title").toLowerCase()::startsWith))
                .collect(Collectors.toSet());

        LOGGER.info("Indexing " + types.size() + " types for " + javadocName);

        int i = 0;
        int previousPercentage = 0;
        for (final Element element : types) {
            final int percentage = (int) ((100D / types.size()) * i++);

            if (percentage % 10 == 0 && percentage != previousPercentage) {
                LOGGER.info(percentage + "% done on type indexing for " + javadocName);
                previousPercentage = percentage;
            }

            final Document page = connect(element.absUrl("href"));

            if (page == null) continue;

            objects.addAll(JavadocPageDeserializer.deserialize(page));
        }

        final Map<String, DocumentedObject> map = new HashMap<>();

        objects.forEach(object -> {
            map.put(DataUtils.getFqn(object).toLowerCase(), object);
            map.put(DataUtils.getName(object).toLowerCase(), object);
        });

        LOGGER.info("Indexing type children with parent methods for " + javadocName);

        i = 0;
        previousPercentage = 0;
        for (final DocumentedObject type : objects) {
            final int percentage = (int) ((100D / objects.size()) * i++);

            if (percentage % 10 == 0 && percentage != previousPercentage) {
                LOGGER.info(percentage + "% done on child method indexing for " + javadocName);
                previousPercentage = percentage;
            }

            if (!DataUtils.isType(type)) {
                continue;
            }

            getChildren(map, type).forEach(heir -> {
                ((TypeMetadata) type.getMetadata()).getMethods().stream()
                        .map(String::toLowerCase)
                        .map(map::get)
                        .forEach(method -> {
                            final String addendum = '#' + method.getName().toLowerCase();

                            map.put(DataUtils.getFqn(heir).toLowerCase() + addendum, method);
                            map.put(DataUtils.getName(heir).toLowerCase() + addendum, method);
                        });
            });
        }

        LOGGER.info("Finished indexing " + javadocName);
        return map;
    }

    @Nullable
    private static Document connect(@NotNull final String url) {
        try {
            return Jsoup.connect(url).maxBodySize(0).timeout(10000).get();
        } catch (IOException exception) {
            LOGGER.error("Something went wrong when connecting to " + url, exception);
        }

        return null;
    }

    @NotNull
    private static Set<DocumentedObject> getChildren(@NotNull final Map<String, DocumentedObject> map, @NotNull final DocumentedObject object) {
        final TypeMetadata typeMetadata = (TypeMetadata) object.getMetadata();

        final Set<DocumentedObject> subClasses = convertFromFqn(map, typeMetadata.getSubClasses());
        final Set<DocumentedObject> subInterfaces = convertFromFqn(map, typeMetadata.getSubInterfaces());
        final Set<DocumentedObject> implementingClasses = convertFromFqn(map, typeMetadata.getImplementingClasses());

        if (subClasses.isEmpty() && subInterfaces.isEmpty() && implementingClasses.isEmpty()) {
            return Collections.emptySet();
        }

        return Stream.of(
                subClasses,
                subInterfaces,
                implementingClasses
        )
                .flatMap(Set::stream)
                .flatMap(heir -> Stream.concat(Stream.of(heir), getChildren(map, heir).stream()))
                .collect(Collectors.toSet());
    }

    @NotNull
    private static Set<DocumentedObject> convertFromFqn(@NotNull final Map<String, DocumentedObject> map,
                                                        @NotNull final Set<String> fqns) {
        return fqns.stream()
                .map(String::toLowerCase)
                .map(map::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }
}
