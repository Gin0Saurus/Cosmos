package dev.saurus.cosmos.core.catalog;

import dev.saurus.cosmos.core.body.Body;

import java.util.List;

/** A star (catalogue index) with its bodies. Body positions are relative to the star, ICRS axes, metres. */
public record StarSystem(String name, int starIndex, Body star, List<Body> planets) {}
