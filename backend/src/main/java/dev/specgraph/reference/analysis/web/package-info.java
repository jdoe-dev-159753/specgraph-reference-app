/**
 * Inbound HTTP adapter for starting analyses and reading retained results.
 *
 * <p>Transport requests are mapped to application use cases, and application failure reasons are
 * mapped to HTTP responses without moving transport semantics into the application contracts.
 */
package dev.specgraph.reference.analysis.web;
