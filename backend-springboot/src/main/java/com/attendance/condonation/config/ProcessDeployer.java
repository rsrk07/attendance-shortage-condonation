package com.attendance.condonation.config;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.DeployResourceCommandStep1;
import io.camunda.client.api.command.DeployResourceCommandStep1.DeployResourceCommandStep2;
import io.camunda.client.api.response.DeploymentEvent;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

/**
 * Deploys the BPMN, DMN and form files from bpmn-models/ (packaged under classpath:bpmn/) to the
 * Camunda cluster on startup. Unchanged files do not create new versions. A failed deployment is
 * logged and does not stop the application.
 */
@Component
@Order(2)
public class ProcessDeployer implements ApplicationRunner {

  private static final Logger log = LoggerFactory.getLogger(ProcessDeployer.class);
  private static final List<String> PATTERNS =
      List.of("classpath*:bpmn/*.bpmn", "classpath*:bpmn/*.dmn", "classpath*:bpmn/forms/*.form");

  private final CamundaClient client;
  private final AppProperties properties;

  public ProcessDeployer(CamundaClient client, AppProperties properties) {
    this.client = client;
    this.properties = properties;
  }

  @Override
  public void run(ApplicationArguments args) {
    if (!properties.deployOnStartup()) {
      log.info("DEPLOY_ON_STARTUP=false - skipping deployment of the process models");
      return;
    }
    try {
      List<Resource> resources = findResources();
      if (resources.isEmpty()) {
        log.warn("No BPMN/DMN/form files found on the classpath - nothing to deploy");
        return;
      }
      DeployResourceCommandStep1 command = client.newDeployResourceCommand();
      DeployResourceCommandStep2 step = null;
      for (Resource resource : resources) {
        try (InputStream in = resource.getInputStream()) {
          byte[] bytes = in.readAllBytes();
          step = (step == null ? command : step).addResourceBytes(bytes, resource.getFilename());
        }
      }
      DeploymentEvent deployment = step.send().join();
      log.info(
          "Deployed to Camunda (deployment {}): {} process(es), {} decision(s), {} form(s)",
          deployment.getKey(),
          deployment.getProcesses().size(),
          deployment.getDecisions().size(),
          deployment.getForm().size());
      deployment
          .getProcesses()
          .forEach(p -> log.info("  process {} version {}", p.getBpmnProcessId(), p.getVersion()));
    } catch (Exception e) {
      log.error(
          "Could not deploy the process models to Camunda: {}. Check CAMUNDA_CLUSTER_ID, CAMUNDA_REGION,"
              + " CAMUNDA_CLIENT_ID and CAMUNDA_CLIENT_SECRET.",
          e.getMessage());
    }
  }

  private List<Resource> findResources() throws IOException {
    PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
    List<Resource> resources = new ArrayList<>();
    for (String pattern : PATTERNS) {
      for (Resource resource : resolver.getResources(pattern)) {
        if (resource.isReadable() && resource.getFilename() != null) {
          resources.add(resource);
        }
      }
    }
    return resources;
  }
}
