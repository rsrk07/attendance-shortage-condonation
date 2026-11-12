package com.attendance.condonation.service;

import com.attendance.condonation.config.AppProperties;
import com.attendance.condonation.entity.Role;
import com.attendance.condonation.exception.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientHttpException;
import io.camunda.client.api.search.enums.UserTaskState;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.UserTask;
import io.camunda.client.api.search.response.Variable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** All calls to the Camunda 8 cluster (Orchestration Cluster REST API through the Java client). */
@Service
public class ProcessService {

  private static final Logger log = LoggerFactory.getLogger(ProcessService.class);
  private static final int PAGE_LIMIT = 200;

  private final CamundaClient client;
  private final ObjectMapper objectMapper;
  private final AppProperties properties;

  public ProcessService(CamundaClient client, ObjectMapper objectMapper, AppProperties properties) {
    this.client = client;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  /** Starts the main condonation process and returns the process instance key. */
  public long startCondonation(Map<String, Object> variables) {
    return call(
        "start the condonation process",
        () ->
            client
                .newCreateInstanceCommand()
                .bpmnProcessId(properties.processId())
                .latestVersion()
                .variables(variables)
                .send()
                .join()
                .getProcessInstanceKey());
  }

  /** Open user tasks for a role: students see tasks assigned to them, staff see their candidate group. */
  public List<UserTask> openTasksFor(Role role, String username) {
    return call(
        "load your tasks",
        () ->
            client
                .newUserTaskSearchRequest()
                .filter(
                    f -> {
                      f.state(UserTaskState.CREATED);
                      if (role == Role.STUDENT) {
                        f.assignee(username);
                      } else {
                        f.candidateGroup(role.candidateGroup());
                      }
                    })
                .page(p -> p.limit(PAGE_LIMIT))
                .send()
                .join()
                .items());
  }

  public UserTask getTask(long userTaskKey) {
    try {
      return client.newUserTaskGetRequest(userTaskKey).send().join();
    } catch (RuntimeException e) {
      if (httpStatus(e) == 404) {
        throw ApiException.notFound("Task " + userTaskKey + " was not found. It may already be completed.");
      }
      throw ApiException.unavailable("Camunda could not load the task: " + e.getMessage(), e);
    }
  }

  public void assignTask(long userTaskKey, String assignee) {
    call(
        "assign the task",
        () ->
            client
                .newAssignUserTaskCommand(userTaskKey)
                .assignee(assignee)
                .allowOverride(false)
                .send()
                .join());
  }

  public void completeTask(long userTaskKey, Map<String, Object> variables) {
    call(
        "complete the task",
        () -> client.newCompleteUserTaskCommand(userTaskKey).variables(variables).send().join());
  }

  public Optional<ProcessInstance> findProcessInstance(long processInstanceKey) {
    try {
      return Optional.of(client.newProcessInstanceGetRequest(processInstanceKey).send().join());
    } catch (RuntimeException e) {
      if (httpStatus(e) == 404) {
        return Optional.empty();
      }
      throw ApiException.unavailable("Camunda could not load the process instance: " + e.getMessage(), e);
    }
  }

  /** All element instances (tasks, events, gateways, sub-processes) of one process instance. */
  public List<ElementInstance> elementInstances(long processInstanceKey) {
    return call(
        "load the process progress",
        () ->
            client
                .newElementInstanceSearchRequest()
                .filter(f -> f.processInstanceKey(processInstanceKey))
                .page(p -> p.limit(PAGE_LIMIT))
                .send()
                .join()
                .items());
  }

  /**
   * Variables in the root scope of a process instance, with JSON values parsed into plain Java
   * objects (Map, List, String, Number, Boolean).
   */
  public Map<String, Object> rootVariables(long processInstanceKey) {
    List<Variable> variables =
        call(
            "load the process variables",
            () ->
                client
                    .newVariableSearchRequest()
                    .filter(f -> f.processInstanceKey(processInstanceKey).scopeKey(processInstanceKey))
                    .page(p -> p.limit(PAGE_LIMIT))
                    .send()
                    .join()
                    .items());
    Map<String, Object> result = new LinkedHashMap<>();
    for (Variable variable : variables) {
      String json = variable.getValue();
      if (Boolean.TRUE.equals(variable.isTruncated())) {
        json = client.newVariableGetRequest(variable.getVariableKey()).send().join().getValue();
      }
      result.put(variable.getName(), parseJson(json));
    }
    return result;
  }

  /** Reads one variable visible in a process instance (any scope); empty if it does not exist. */
  public Optional<Object> findVariable(long processInstanceKey, String name) {
    List<Variable> variables =
        call(
            "load the process variable " + name,
            () ->
                client
                    .newVariableSearchRequest()
                    .filter(f -> f.processInstanceKey(processInstanceKey).name(name))
                    .page(p -> p.limit(10))
                    .send()
                    .join()
                    .items());
    return variables.stream()
        .filter(v -> Objects.equals(processInstanceKey, v.getScopeKey()))
        .findFirst()
        .or(() -> variables.stream().findFirst())
        .map(v -> parseJson(v.getValue()));
  }

  /** HTTP status of a failed Camunda REST call, or -1 when the error was not an HTTP problem. */
  static int httpStatus(Throwable error) {
    for (Throwable t = error; t != null; t = t.getCause()) {
      if (t instanceof ClientHttpException http) {
        return http.code();
      }
    }
    return -1;
  }

  private Object parseJson(String json) {
    if (json == null) {
      return null;
    }
    try {
      return objectMapper.readValue(json, Object.class);
    } catch (JsonProcessingException e) {
      return json;
    }
  }

  private <T> T call(String action, Supplier<T> supplier) {
    try {
      return supplier.get();
    } catch (ApiException e) {
      throw e;
    } catch (RuntimeException e) {
      log.warn("Camunda call failed while trying to {}: {}", action, e.getMessage());
      throw ApiException.unavailable(
          "The workflow engine is not reachable right now (could not " + action + "). Please try again.", e);
    }
  }
}
