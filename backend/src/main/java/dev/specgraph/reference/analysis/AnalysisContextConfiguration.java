package dev.specgraph.reference.analysis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Enables the provider-neutral context bounds consumed before any model-adapter call. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AnalysisContextProperties.class)
class AnalysisContextConfiguration {}
