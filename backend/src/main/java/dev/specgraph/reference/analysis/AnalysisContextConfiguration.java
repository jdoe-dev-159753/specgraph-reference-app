package dev.specgraph.reference.analysis;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AnalysisContextProperties.class)
class AnalysisContextConfiguration {}
