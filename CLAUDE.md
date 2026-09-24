# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Status

This project has no code yet. Update this file when the package layout and the test setup exist.

## Build

The project uses Maven.

- Build: `mvn package`
- Run all tests: `mvn test`
- Run one test: `mvn test -Dtest=ClassName#methodName`

## Purpose

A calendar agent, written in Java, that acts as the user's personal secretary. It must:

- Schedule appointments and alarms.
- Edit or remove appointments, alarms and notes when the user asks.
- Tell the user when an appointment is due.
- Tell the user what is on a given day when the user asks.

## Model

The agent calls its language model through Ollama. The model is an Ollama cloud model that is already set up (`gpt-oss:120b-cloud`, reached through the local Ollama app at `http://localhost:11434`). It is not a local model and it is not the Anthropic API.

This is a training exercise in LLM tool use. The agent loop and the HTTP calls to `/api/chat` are written by hand. Do not add an agent library such as LangChain4j.

The code targets Java 11 (the installed version). Do not use `record` types, text blocks or other features newer than Java 11.

The full design is in `docs/superpowers/specs/2026-09-24-calendar-agent-design.md`.

## Design

The agent does all of its work through tools. Planned tools include:

- `set-appointment`
- `set-alarm`
- `add-note`
- `edit`
- `remove`

More tools may be added. Keep each capability behind a named tool, so the agent never changes calendar data outside a tool call.

## Storage

Appointments, alarms and notes are stored in a JSON file.

## User interface

For now, everything runs in the terminal. A graphical UI comes later, after the agent works. Keep the agent logic separate from the terminal input and output, so a UI can replace the terminal later.

The user talks to the agent in the terminal. Due alerts also appear in the terminal. Alerts show only while the agent runs. This limit is accepted for now.

## Code style

Follow Clean Code guidelines in all code, plans and specs:

- Meaningful names for classes, methods, fields, parameters and local variables. No abbreviations.
- Single responsibility: each class has one job, stated in its class comment. Split a class when that comment needs "and".
- Short methods that do one thing. Give each step of a longer method its own private method with a clear name.
- Human friendly: code reads top-down. Every message to the user or to the model is a plain, complete sentence that says what to do next.
