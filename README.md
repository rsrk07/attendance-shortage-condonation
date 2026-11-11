# Attendance Shortage Condonation

A full-stack workflow automation application built with **Camunda 8**, **Spring Boot**, **React** and an **H2** database.

Students whose attendance is below 75% apply for condonation with supporting documents. The workflow fetches their attendance through a REST connector, decides the eligibility slab and condonation fee with a DMN table, verifies the documents, routes the request through the required approvers (Faculty Advisor, Academic Advisor, HOD) and, once approved, collects the fee and updates the student record.

> 🚧 Work in progress — sections below are filled in as each part of the project is completed.

## Repository structure

```
attendance-shortage-condonation/
├── bpmn-models/          Main BPMN process, child processes (Call Activities), DMN, Camunda Forms
├── backend-springboot/   REST APIs, JPA entities, repositories, services, Zeebe job workers
├── frontend-react/       React components, Vite config, Tailwind CSS, Tasklist UI
├── docker-compose.yml    Containerized deployment (added in Part 7)
└── README.md
```

## Project overview
_To be completed._

## Technology stack
_To be completed._

## System architecture
_To be completed._

## Setup and execution
_To be completed._

## FEEL expression dictionary
_To be completed._

## Configuration and deployment
_To be completed._
